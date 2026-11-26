package dev.jandoant.fuel.data.repo

import dev.jandoant.fuel.core.domain.GarminDay
import dev.jandoant.fuel.core.domain.Measurement
import dev.jandoant.fuel.core.domain.MeasurementType
import dev.jandoant.fuel.core.domain.Phase
import dev.jandoant.fuel.core.domain.PlanEntry
import dev.jandoant.fuel.core.model.BalanceFromWeight
import dev.jandoant.fuel.core.model.BandPoint
import dev.jandoant.fuel.core.model.CalorieZone
import dev.jandoant.fuel.core.model.CalorieZoneModel
import dev.jandoant.fuel.core.model.CheckInDay
import dev.jandoant.fuel.core.model.CheckInModel
import dev.jandoant.fuel.core.model.CheckInResult
import dev.jandoant.fuel.core.model.CompositionTrend
import dev.jandoant.fuel.core.model.DailyWeight
import dev.jandoant.fuel.core.model.DaySnapshot
import dev.jandoant.fuel.core.model.Forecast
import dev.jandoant.fuel.core.model.GarminCalibration
import dev.jandoant.fuel.core.model.GarminCalibrationModel
import dev.jandoant.fuel.core.model.GoalRate
import dev.jandoant.fuel.core.model.MacroNumbers
import dev.jandoant.fuel.core.model.MacroTargets
import dev.jandoant.fuel.core.model.MicroReferences
import dev.jandoant.fuel.core.model.ModelRun
import dev.jandoant.fuel.core.model.ModelSettings
import dev.jandoant.fuel.core.model.Nutrients
import dev.jandoant.fuel.core.model.TargetsEngine
import dev.jandoant.fuel.core.model.TrendPoint
import dev.jandoant.fuel.core.model.TrendWeight
import dev.jandoant.fuel.core.model.WeightReading
import dev.jandoant.fuel.core.model.WeightRegression
import dev.jandoant.fuel.data.db.ForecastEntity
import dev.jandoant.fuel.data.db.FuelDatabase
import dev.jandoant.fuel.data.db.ModelSnapshotEntity
import dev.jandoant.fuel.data.db.PhaseEntity
import dev.jandoant.fuel.data.settings.SettingsRepository
import dev.jandoant.fuel.data.settings.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Planned and eaten totals of one day. [eaten] is null on days without a food log (never 0 kcal). */
data class DayTotals(
    val date: LocalDate,
    val planned: MacroNumbers,
    val eaten: MacroNumbers?,
    val eatenNutrients: Nutrients,
    val plannedNutrients: Nutrients,
    val itemCount: Int,
    val itemsWithFullData: Int,
)

/** Everything the model knows right now; the screens derive their state from this. */
data class ModelState(
    val today: LocalDate,
    val settings: UserSettings,
    val phase: Phase,
    val phases: List<Phase>,
    val run: ModelRun,
    val measurements: List<Measurement>,
    /** All daily weigh-ins (all phases, incl. imported history). */
    val dailyWeights: List<DailyWeight>,
    /** Trend over all history (for Body "All time"); the model itself uses only the phase. */
    val trendAll: List<TrendPoint>,
    val bodyFat: List<BandPoint>,
    val leanMass: List<BandPoint>,
    val days: Map<LocalDate, DayTotals>,
    val snapshots: List<ModelSnapshotEntity>,
    val checkIns: List<CheckInResult>,
    val forecasts: List<Forecast>,
    val garmin: GarminCalibration?,
    val garminDays: List<GarminDay>,
) {
    val snapshotToday: DaySnapshot get() = run.today
    val zoneToday: CalorieZone? get() = run.today.zone
    val targetsToday: MacroTargets? get() = run.today.macroTargets
    val trendToday: Double? get() = run.today.trendKg
    val balance: BalanceFromWeight? get() = run.balance

    /** Future days use the current zone (PRD §6.4); past days their own snapshot. */
    fun zoneFor(date: LocalDate): CalorieZone? =
        if (!date.isBefore(today)) zoneToday
        else run.days.firstOrNull { it.date == date }?.zone ?: snapshots.firstOrNull { it.date == date.toEpochDay() }?.let { s ->
            if (s.zoneLow != null && s.zoneHigh != null && s.target != null) CalorieZone(s.zoneLow, s.zoneHigh, s.target) else null
        }

    fun targetsFor(date: LocalDate): MacroTargets? =
        if (!date.isBefore(today)) targetsToday else run.days.firstOrNull { it.date == date }?.macroTargets ?: targetsToday

    /** Logged intake (kcal) per logged day before today — the model input. */
    val intakeByDay: Map<LocalDate, Double>
        get() = days.values.filter { it.eaten != null && it.date.isBefore(today) }.associate { it.date to it.eaten!!.kcal.toDouble() }
}

/**
 * Runs the pure TargetsEngine whenever weigh-ins, food logs, settings or phases change (off the main thread),
 * persists daily snapshots and weekly forecasts, and builds the weekly check-ins.
 */
class ModelRepository(
    private val db: FuelDatabase,
    private val settings: SettingsRepository,
    private val foods: FoodRepository,
    private val scope: CoroutineScope,
    private val clock: () -> LocalDate = { LocalDate.now() },
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val todayFlow = MutableDay(clock)

    val dayTotals: Flow<Map<LocalDate, DayTotals>> = combine(
        db.planDao().observeAllEntries(), db.planDao().observeAllIngredients(), foods.catalog,
    ) { e, i, cat -> totalsByDay(PlanRepository.assemble(e, i, cat)) }

    val phases: Flow<List<Phase>> = db.modelDao().observePhases().map { list -> list.map { it.toDomain() } }

    private val inputs = combine(
        db.bodyDao().observeAll(), dayTotals, settings.settings, phases, todayFlow.flow,
    ) { m, d, s, p, t -> Inputs(m.map { it.toDomain() }, d, s, p, t) }

    private val garminFlow = db.bodyDao().observeGarmin()

    val state: StateFlow<ModelState?> = combine(inputs, garminFlow, db.modelDao().observeSnapshots(), db.modelDao().observeForecasts()) { inp, g, snaps, fc ->
        compute(inp, g.map { GarminDay(LocalDate.ofEpochDay(it.date), it.totalKcal, it.activeKcal, it.origin) }, snaps, fc)
    }.conflate().flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch { ensurePhase() }
        // Persist outputs whenever the model changes.
        scope.launch(Dispatchers.Default) {
            state.collect { s -> if (s != null) persist(s) }
        }
    }

    /** Call when the date may have changed (app resume, daily worker). */
    fun refreshDay() = todayFlow.refresh()

    private data class Inputs(
        val measurements: List<Measurement>,
        val days: Map<LocalDate, DayTotals>,
        val settings: UserSettings,
        val phases: List<Phase>,
        val today: LocalDate,
    )

    suspend fun ensurePhase(defaultMaintenance: Double = 2500.0) {
        val dao = db.modelDao()
        if (dao.phases().none { !it.scaleHistoryOnly }) {
            dao.insertPhase(PhaseEntity(name = "Phase 1", startDate = clock().toEpochDay(), startingMaintenance = defaultMaintenance))
        }
    }

    // --- Phases (PRD §7.12) ---

    suspend fun setPhaseStart(phaseId: Long, start: LocalDate) {
        val dao = db.modelDao()
        val p = dao.phases().firstOrNull { it.id == phaseId } ?: return
        dao.updatePhase(p.copy(startDate = minOf(start, clock()).toEpochDay()))
    }

    suspend fun setStartingMaintenance(phaseId: Long, kcal: Double) {
        val dao = db.modelDao()
        val p = dao.phases().firstOrNull { it.id == phaseId } ?: return
        dao.updatePhase(p.copy(startingMaintenance = kcal.coerceIn(1000.0, 6000.0)))
    }

    /** "Start fresh from today": closes the current phase and opens a new one. Nothing is deleted. */
    suspend fun startFresh(startingMaintenance: Double) = db.tx {
        val dao = db.modelDao()
        val today = clock()
        val all = dao.phases().filter { !it.scaleHistoryOnly }
        all.filter { it.endDate == null }.forEach { dao.updatePhase(it.copy(endDate = today.minusDays(1).toEpochDay())) }
        dao.insertPhase(PhaseEntity(name = "Phase ${all.size + 1}", startDate = today.toEpochDay(), startingMaintenance = startingMaintenance))
    }

    private fun compute(inp: Inputs, garmin: List<GarminDay>, snaps: List<ModelSnapshotEntity>, fc: List<ForecastEntity>): ModelState? {
        val phases = inp.phases.filter { !it.scaleHistoryOnly }
        val phase = phases.lastOrNull { it.endDate == null } ?: phases.lastOrNull() ?: return null
        val today = inp.today
        val s = inp.settings
        val defaults = s.modelDefaults()
        val tz = zone()

        val weights = inp.measurements.filter { it.type == MeasurementType.WEIGHT }
        val readings = weights.map { WeightReading(it.time.atZone(tz).toLocalDateTime(), it.value) }
        val daily = TrendWeight.dailyWeights(readings)
        val trendAll = TrendWeight.smooth(daily, defaults.trendAlphaPerDay)
        val intake = inp.days.values.filter { it.eaten != null && it.date.isBefore(today) }.associate { it.date to it.eaten!!.kcal.toDouble() }
        val start = minOf(phase.startDate, today)
        val run = TargetsEngine.run(readings, intake, ModelSettings(start, phase.startingMaintenanceKcal, s.goal, defaults), today)

        fun bandSeries(type: MeasurementType) = CompositionTrend.smooth(
            TrendWeight.dailyWeights(inp.measurements.filter { it.type == type }.map { WeightReading(it.time.atZone(tz).toLocalDateTime(), it.value) }),
            defaults.trendAlphaPerDay,
        )
        val fat = bandSeries(MeasurementType.BODY_FAT)
        val lean = bandSeries(MeasurementType.LEAN_MASS)

        val checkIns = buildCheckIns(run, inp.days, daily, start, s, fat, lean, trendPhase = run.trend)
        val forecasts = fc.map { Forecast(LocalDate.ofEpochDay(it.weekEnd), it.expectedKg, it.actualKg) }

        val garminCal = if (s.garminCalibration) {
            val ends = (0 until 26).map { today.minusDays(1 + 7L * it) }.reversed()
            GarminCalibrationModel.calibrate(
                GarminCalibrationModel.weeks(intake, daily, garmin.associate { it.date to (it.totalKcal to it.activeKcal) }, ends, defaults),
            )
        } else null

        return ModelState(
            today = today, settings = s, phase = phase, phases = inp.phases, run = run, measurements = inp.measurements,
            dailyWeights = daily, trendAll = trendAll, bodyFat = fat, leanMass = lean, days = inp.days, snapshots = snaps,
            checkIns = checkIns, forecasts = forecasts, garmin = garminCal, garminDays = garmin,
        )
    }

    private fun buildCheckIns(
        run: ModelRun,
        days: Map<LocalDate, DayTotals>,
        daily: List<DailyWeight>,
        phaseStart: LocalDate,
        s: UserSettings,
        fat: List<BandPoint>,
        lean: List<BandPoint>,
        trendPhase: List<TrendPoint>,
    ): List<CheckInResult> {
        val defaults = s.modelDefaults()
        val snapByDate = run.days.associateBy { it.date }
        return run.weeklyUpdates.map { u ->
            val end = u.weekEnd
            val startW = maxOf(phaseStart, end.minusDays(6))
            val weekDays = generateSequence(startW) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { d ->
                val t = days[d]
                CheckInDay(d, t?.planned?.takeIf { it.kcal > 0 }, t?.eaten, snapByDate[d]?.zone, snapByDate[d]?.macroTargets)
            }.toList()
            val weighIns = daily.count { !it.date.isBefore(startW) && !it.date.isAfter(end) }
            val balance = WeightRegression.balance(daily, end, defaults, from = phaseStart)
            val trendEnd = TrendWeight.trendOn(trendPhase, end)
            val rate = if (balance != null && trendEnd != null) GoalRate.check(GoalRate.actualPctPerWeek(balance.slope, trendEnd), s.goal, defaults) else null
            val trendStart = TrendWeight.trendOn(trendPhase, startW.minusDays(1)) ?: trendPhase.firstOrNull { !it.date.isBefore(startW) }?.trendKg
            val before = snapByDate[end.minusDays(1)]?.zone?.target ?: snapByDate[end]?.zone?.target
            val after = if (trendEnd != null) CalorieZoneModel.rawZone(u.posterior.kcal, trendEnd, s.goal, defaults.kcalPerKg).target else null
            val comp = CheckInModel.composition(
                fat.map { TrendPoint(it.date, it.value, it.trend) }, lean.map { TrendPoint(it.date, it.value, it.trend) },
                trendPhase, end.minusDays(27), end,
            )
            CheckInModel.evaluate(weekDays, weighIns, rate, trendStart, trendEnd, s.goal, s.goalWeightKg, u, before, after, comp)
        }.reversed()
    }

    private suspend fun persist(s: ModelState) {
        val dao = db.modelDao()
        val existing = s.snapshots.associateBy { it.date }
        val changed = s.run.days.map { d ->
            ModelSnapshotEntity(
                date = d.date.toEpochDay(), phaseId = s.phase.id, trendKg = d.trendKg, maintenance = d.maintenance.kcal,
                maintenanceSd = d.maintenance.sd, zoneLow = d.zone?.low, zoneHigh = d.zone?.high, target = d.zone?.target,
                proteinMin = d.macroTargets?.protein?.min, proteinMax = d.macroTargets?.protein?.max,
                fatMin = d.macroTargets?.fat?.min, fatMax = d.macroTargets?.fat?.max, fiberMin = d.macroTargets?.fiber?.min,
                targetHeld = d.targetHeld,
            )
        }.filter { new -> existing[new.date] != new }
        if (changed.isNotEmpty()) dao.upsertSnapshots(changed)

        // Weekly forecasts (PRD §6.7.3): stored once per phase week, outcome filled in after the week.
        val start = s.phase.startDate
        var weekEnd = start.plusDays(6)
        val known = s.forecasts.associateBy { it.weekEnd }
        while (!weekEnd.isAfter(s.today.plusDays(6))) {
            val wStart = weekEnd.minusDays(6)
            val existing = known[weekEnd]
            if (existing == null) {
                val planned = generateSequence(wStart) { it.plusDays(1) }.takeWhile { !it.isAfter(weekEnd) }
                    .mapNotNull { s.days[it]?.planned?.kcal?.takeIf { k -> k > 0 } }.toList()
                val maint = s.run.days.lastOrNull { !it.date.isAfter(wStart) }?.maintenance?.kcal
                if (planned.isNotEmpty() && maint != null) {
                    dao.insertForecast(ForecastEntity(weekEnd.toEpochDay(), CheckInModel.forecastKg(planned.average(), maint)))
                }
            }
            if (weekEnd.isBefore(s.today) && (existing == null || existing.actualTrendChangeKg == null)) {
                val t0 = TrendWeight.trendOn(s.run.trend, wStart.minusDays(1))
                val t1 = TrendWeight.trendOn(s.run.trend, weekEnd)
                if (t0 != null && t1 != null) dao.setForecastOutcome(weekEnd.toEpochDay(), t1 - t0)
            }
            weekEnd = weekEnd.plusDays(7)
        }
    }

    companion object {
        fun totalsByDay(entries: List<PlanEntry>): Map<LocalDate, DayTotals> =
            entries.groupBy { it.date }.mapValues { (date, list) ->
                val eaten = list.filter { it.eaten }
                DayTotals(
                    date = date,
                    planned = MacroNumbers.sum(list.map { it.numbers }),
                    eaten = if (eaten.isEmpty()) null else MacroNumbers.sum(eaten.map { it.numbers }),
                    eatenNutrients = Nutrients.sum(eaten.map { it.nutrients }),
                    plannedNutrients = Nutrients.sum(list.map { it.nutrients }),
                    itemCount = list.sumOf { it.portions.size },
                    itemsWithFullData = list.sumOf { e -> e.portions.count { MicroReferences.hasFullData(it.food.per100g) } },
                )
            }
    }
}

fun PhaseEntity.toDomain() = Phase(id, name, LocalDate.ofEpochDay(startDate), endDate?.let { LocalDate.ofEpochDay(it) }, startingMaintenance, scaleHistoryOnly)

fun dev.jandoant.fuel.data.db.MeasurementEntity.toDomain() =
    Measurement(Instant.ofEpochMilli(time), MeasurementType.valueOf(type), value, origin)

/** A "today" that can be re-read (date changes at midnight / on resume). */
private class MutableDay(private val clock: () -> LocalDate) {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(clock())
    val flow: Flow<LocalDate> = state
    fun refresh() { state.value = clock() }
}
