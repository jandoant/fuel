package dev.jandoant.fuel.core.model

/**
 * Every tunable constant of the model, in one place (PRD §6: "All constants below are defaults").
 * Values marked CHOICE are not fixed by the PRD; see docs/maths-assumptions.md (confirmed as decisions #26–28).
 */
data class ModelDefaults(
    /** kcal stored in 1 kg of body weight change (PRD §6.2). */
    val kcalPerKg: Double = 7700.0,

    // --- Trend weight (§6.1) ---
    /** Smoothing per day. PRD says ≈0.1–0.15. CHOICE: 0.1. */
    val trendAlphaPerDay: Double = 0.10,

    // --- Balance from weight (§6.2) ---
    val regressionWindowDays: Int = 28,
    /** CHOICE: minimum weigh-ins for a regression. */
    val minWeighInsForRegression: Int = 3,
    /** CHOICE: "likely range" = ±1 standard error (≈68 %, the usual meaning of "likely"). */
    val likelyRangeZ: Double = 1.0,

    // --- Maintenance (§6.3) ---
    val maintenanceWindowDays: Int = 28,
    /** Bootstrap length: until this many days exist in the phase, the estimate is trusted less. */
    val bootstrapDays: Int = 21,
    /** Weeks with more missing log days than this do not adapt maintenance. */
    val maxMissingLogDaysPerWeek: Int = 2,
    /** A weight gap of at least this many days without a weigh-in means "adapt cautiously". */
    val cautiousWeightGapDays: Int = 2,
    /** A weight gap of at least this many days freezes maintenance and holds the target. */
    val freezeWeightGapDays: Int = 7,
    /** CHOICE: observation noise is multiplied by this during a 2–6 day weight gap. */
    val cautiousNoiseFactor: Double = 2.0,
    /** CHOICE: starting uncertainty (1 sd) of the user's starting maintenance. */
    val startingMaintenanceSd: Double = 300.0,
    /** CHOICE: how much true maintenance may drift per week (1 sd), the Kalman process noise. */
    val maintenanceDriftSdPerWeek: Double = 50.0,
    /** CHOICE: noise floor for logging bias/label error per observation (1 sd). */
    val loggingNoiseFloorSd: Double = 100.0,

    // --- Goal → calorie zone (§6.4) ---
    val maxDailyTargetChangeKcal: Double = 50.0,
    val calorieFloorKcal: Double = 1800.0,
    /** Warn when the actual loss rate is faster than this (%/week, positive number). */
    val fastLossWarningPctPerWeek: Double = 1.0,
    /** CHOICE (from the Energy prototype): "Maintain" counts as on track within ±0.1 %/week. */
    val maintainTolerancePctPerWeek: Double = 0.1,
    /** Display rounding for calorie numbers (prototype rounds to 10). */
    val calorieDisplayStep: Int = 10,

    // --- Macro & fiber targets (§6.5) ---
    val macros: MacroSettings = MacroSettings(),
)

data class MacroSettings(
    val proteinGPerKg: ClosedFloatingPointRange<Double> = 1.8..2.0,
    val fatGPerKg: ClosedFloatingPointRange<Double> = 0.5..0.8,
    val fiberMinG: Int = 30,
    /** Reference-weight window: 7, 14 or 28 days. */
    val referenceWindowDays: Int = 14,
) {
    init {
        require(referenceWindowDays in setOf(7, 14, 28)) { "Reference window must be 7, 14 or 28 days" }
    }
}
