package dev.jandoant.fuel.ui.components

import dev.jandoant.fuel.core.model.CalorieLine
import dev.jandoant.fuel.core.model.GramRange
import dev.jandoant.fuel.core.model.NutrientStatus
import dev.jandoant.fuel.core.model.StatusTone
import dev.jandoant.fuel.core.model.tone

/** Status wording from the prototypes (PRD §6.6). Colour always comes with text. */
object StatusText {

    fun tone(s: NutrientStatus): Tone = when (s.tone) {
        StatusTone.OK -> Tone.OK
        StatusTone.WARNING -> Tone.WARNING
        StatusTone.DANGER -> Tone.DANGER
        StatusTone.NONE -> Tone.NEUTRAL
    }

    /** Short chip text under a macro ring: "fits", "11 g short", "8 g over", "eaten 3 g over". */
    fun macroShort(s: NutrientStatus): String = when (s) {
        is NutrientStatus.Fits -> "fits"
        is NutrientStatus.Short -> "${s.amount} g short"
        is NutrientStatus.Over -> "${s.amount} g over"
        is NutrientStatus.EatenOver -> "eaten ${s.amount} g over"
        NutrientStatus.NoTarget -> "no target"
    }

    /** Calorie status line on Today. */
    fun calories(line: CalorieLine): String = when (val s = line.status) {
        is NutrientStatus.Fits -> {
            val v = s.vsTarget ?: 0
            if (v >= 0) "fits · ${fmt(v)} spare" else "fits · ${fmt(-v)} above target"
        }
        is NutrientStatus.Short -> "plan ${fmt(s.amount)} short"
        is NutrientStatus.Over -> "plan ${fmt(s.amount)} over"
        is NutrientStatus.EatenOver -> "eaten ${fmt(s.amount)} over goal"
        NutrientStatus.NoTarget -> "weigh in to get a target"
    }

    /** Plan-day chip: "Fits target", "140 kcal short", "60 kcal over", "Empty". */
    fun planDay(line: CalorieLine): String = if (line.planned == 0) "Empty" else when (val s = line.status) {
        is NutrientStatus.Fits -> "Fits target"
        is NutrientStatus.Short -> "${fmt(s.amount)} kcal short"
        is NutrientStatus.Over -> "${fmt(s.amount)} kcal over"
        is NutrientStatus.EatenOver -> "eaten ${fmt(s.amount)} over"
        NutrientStatus.NoTarget -> "No target yet"
    }

    /** Effect preview: "fits", "10 short", "5 g over". */
    fun effect(s: NutrientStatus, unit: String): String = when (s) {
        is NutrientStatus.Fits -> "fits"
        is NutrientStatus.Short -> "${fmt(s.amount)}$unit short"
        is NutrientStatus.Over -> "${fmt(s.amount)}$unit over"
        is NutrientStatus.EatenOver -> "${fmt(s.amount)}$unit over"
        NutrientStatus.NoTarget -> "—"
    }

    fun range(r: GramRange?): String = when {
        r == null -> "—"
        r.max == null -> "≥${r.min}"
        else -> "${r.min}–${r.max}"
    }
}
