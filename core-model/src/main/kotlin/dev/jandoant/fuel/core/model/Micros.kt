package dev.jandoant.fuel.core.model

/** Which numbers a micronutrient section shows (PRD §7.13). */
enum class MicroScope(val label: String) {
    /** Eaten so far today: never flags "low" before the day is over. */
    TODAY("so far today"),
    DAY("this day"),
    AVERAGE("daily average"),
}

data class MicroRow(
    val nutrient: Nutrient,
    val name: String,
    val value: Double,
    val reference: Double,
    val isLimit: Boolean,
    val low: Boolean,
    val over: Boolean,
) {
    val ratio: Double get() = if (reference <= 0) 0.0 else value / reference
}

data class MicroReport(
    val limits: List<MicroRow>,
    val vitamins: List<MicroRow>,
    val coverageText: String,
) {
    val lowCount: Int get() = vitamins.count { it.low }
    val overCount: Int get() = limits.count { it.over }

    fun summary(scope: MicroScope): String {
        val parts = buildList {
            if (lowCount > 0) add("$lowCount low")
            if (overCount > 0) add("$overCount over limit")
        }
        return if (parts.isNotEmpty()) parts.joinToString(" · ")
        else if (scope == MicroScope.TODAY) "on track so far" else "all fine"
    }
}

/**
 * DGE reference values for adult men (the user) and WHO/DGE limits. Free sugar is approximated by total sugar
 * (Open Food Facts has no free-sugar field) — the row is labelled "Sugar" to stay honest.
 */
object MicroReferences {
    const val SALT_MAX_G = 6.0
    const val SUGAR_MAX_ENERGY_SHARE = 0.10
    const val SAT_FAT_MAX_ENERGY_SHARE = 0.10
    /** Below this share of the reference a vitamin/mineral counts as "low". */
    const val LOW_RATIO = 0.75

    val DGE: Map<Nutrient, Double> = mapOf(
        Nutrient.POTASSIUM to 4000.0,
        Nutrient.CALCIUM to 1000.0,
        Nutrient.MAGNESIUM to 350.0,
        Nutrient.IRON to 11.0,
        Nutrient.ZINC to 14.0,
        Nutrient.VITAMIN_C to 110.0,
        Nutrient.VITAMIN_D to 20.0,
        Nutrient.VITAMIN_B12 to 4.0,
        Nutrient.FOLATE to 300.0,
    )

    /**
     * [energyKcal] sets the sugar and saturated-fat limits (10 % of energy); pass the day's calorie target, or
     * the eaten energy when there is no target.
     */
    fun report(
        total: Nutrients,
        energyKcal: Double,
        scope: MicroScope,
        itemsWithFullData: Int,
        itemCount: Int,
    ): MicroReport {
        val sugarMax = energyKcal * SUGAR_MAX_ENERGY_SHARE / 4.0
        val satMax = energyKcal * SAT_FAT_MAX_ENERGY_SHARE / 9.0
        fun limit(n: Nutrient, name: String, max: Double) =
            MicroRow(n, name, total[n], max, isLimit = true, low = false, over = max > 0 && total[n] > max)
        val limits = listOf(
            limit(Nutrient.SALT, "Salt", SALT_MAX_G),
            limit(Nutrient.SUGAR, "Sugar", sugarMax),
            limit(Nutrient.SAT_FAT, "Saturated fat", satMax),
        )
        val vitamins = Nutrient.MICROS.map { n ->
            val ref = DGE.getValue(n)
            val v = total[n]
            MicroRow(n, n.label, v, ref, isLimit = false, low = scope != MicroScope.TODAY && v / ref < LOW_RATIO, over = false)
        }
        val coverage = when (scope) {
            MicroScope.AVERAGE -> "averaged over logged days"
            else -> if (itemCount == 0) "no foods yet" else "$itemsWithFullData of $itemCount foods have full data"
        }
        return MicroReport(limits, vitamins, coverage)
    }

    /** A food has full micronutrient data when every tracked micronutrient is known. */
    fun hasFullData(per100g: Nutrients): Boolean = Nutrient.MICROS.all { per100g.has(it) }
}
