package dev.jandoant.fuel.core.model

import java.util.Locale

/** Units a spoken phrase can name. Mass/volume units convert to grams directly; the rest map to serving units. */
enum class SpokenUnit(val gramsPerUnit: Double?, val fallbackGrams: Double?, val synonyms: List<String>) {
    GRAM(1.0, null, listOf("g", "gr", "gramm", "gram", "grams", "gramms")),
    KILOGRAM(1000.0, null, listOf("kg", "kilo", "kilogramm", "kilogram", "kilograms")),
    MILLILITER(1.0, null, listOf("ml", "milliliter", "millilitre", "milliliters")),
    LITER(1000.0, null, listOf("l", "liter", "litre", "liters")),
    TABLESPOON(null, 15.0, listOf("el", "esslöffel", "essloeffel", "tablespoon", "tablespoons", "tbsp")),
    TEASPOON(null, 5.0, listOf("tl", "teelöffel", "teeloeffel", "teaspoon", "teaspoons", "tsp")),
    PINCH(null, 0.5, listOf("prise", "prisen", "pinch")),
    SLICE(null, 30.0, listOf("scheibe", "scheiben", "slice", "slices")),
    HANDFUL(null, 30.0, listOf("handvoll", "hand voll", "handful", "handfuls")),
    CUP(null, 240.0, listOf("tasse", "tassen", "becher", "cup", "cups")),
    GLASS(null, 200.0, listOf("glas", "gläser", "glass", "glasses")),
    SCOOP(null, 30.0, listOf("messlöffel", "scoop", "scoops")),
    SERVING(null, null, listOf("portion", "portionen", "serving", "servings")),
    PIECE(null, null, listOf("stück", "stueck", "stk", "piece", "pieces"));

    companion object {
        private val byWord: Map<String, SpokenUnit> = entries.flatMap { u -> u.synonyms.map { it to u } }.toMap()
        fun of(word: String): SpokenUnit? = byWord[word.lowercase(Locale.GERMAN)]
        /** Longest synonym first, so "hand voll" wins over "hand". */
        val phrases: List<Pair<String, SpokenUnit>> = byWord.entries.map { it.key to it.value }.sortedByDescending { it.first.length }
    }
}

/** One food from a spoken sentence. [amount] is null when nothing was said ("Banane" = one default portion). */
data class SpokenItem(val amount: Double?, val unit: SpokenUnit?, val query: String)

/**
 * Parses dictated food input, German first (the user's language), English too:
 * "200 Gramm Skyr und eine Banane", "ein Esslöffel Olivenöl", "1 tablespoon of olive oil", "zwei Eier".
 */
object VoiceParser {

    private val NUMBER_WORDS = mapOf(
        "ein" to 1.0, "eins" to 1.0, "eine" to 1.0, "einen" to 1.0, "einem" to 1.0, "einer" to 1.0,
        "zwei" to 2.0, "drei" to 3.0, "vier" to 4.0, "fünf" to 5.0, "fuenf" to 5.0, "sechs" to 6.0, "sieben" to 7.0,
        "acht" to 8.0, "neun" to 9.0, "zehn" to 10.0, "elf" to 11.0, "zwölf" to 12.0, "zwanzig" to 20.0, "dreißig" to 30.0,
        "fünfzig" to 50.0, "hundert" to 100.0, "einhundert" to 100.0, "zweihundert" to 200.0, "dreihundert" to 300.0,
        "halb" to 0.5, "halbe" to 0.5, "halben" to 0.5, "halbes" to 0.5, "anderthalb" to 1.5, "eineinhalb" to 1.5,
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0,
        "ten" to 10.0, "half" to 0.5, "hundred" to 100.0,
    )

    /** Words between unit and food that carry no meaning ("1 tablespoon of oil", "200 g vom Skyr"). */
    private val FILLER = setOf("of", "von", "vom", "der", "die", "das", "den", "dem", "some", "etwas", "mit")

    /** Words that separate items (a comma between digits is a decimal comma, not a separator). */
    private val SEPARATORS = Regex("\\s*(?:(?<!\\d),|,(?!\\d)|;|\\bund\\b|\\bsowie\\b|\\bplus\\b|\\band\\b|\\bdazu\\b)\\s*", RegexOption.IGNORE_CASE)

    fun parse(utterance: String): List<SpokenItem> =
        utterance.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { parseItem(it) }

    fun parseItem(text: String): SpokenItem? {
        // Speech recognisers often write "200g" or "1,5"; normalise.
        var rest = text.lowercase(Locale.GERMAN).replace(Regex("(\\d)([a-zäöüß])"), "$1 $2").trim()
        var amount: Double? = null

        val num = Regex("^(\\d+(?:[.,]\\d+)?)\\s*").find(rest)
        if (num != null) {
            amount = num.groupValues[1].replace(',', '.').toDouble()
            rest = rest.substring(num.range.last + 1)
        } else {
            val first = rest.substringBefore(' ')
            NUMBER_WORDS[first]?.let { amount = it; rest = rest.substringAfter(' ', "") }
            // "ein halbes", "eine halbe"
            val next = rest.substringBefore(' ')
            if (amount == 1.0 && (next == "halbe" || next == "halbes" || next == "halben" || next == "half")) {
                amount = 0.5; rest = rest.substringAfter(' ', "")
            }
        }
        rest = rest.trim()

        var unit: SpokenUnit? = null
        for ((phrase, u) in SpokenUnit.phrases) {
            if (rest == phrase || rest.startsWith("$phrase ")) {
                unit = u
                rest = rest.removePrefix(phrase).trim()
                break
            }
        }
        // A unit word without a number ("Prise Salz", "Scheibe Brot") means one.
        if (unit != null && amount == null) amount = 1.0

        val words = rest.split(' ').filter { it.isNotBlank() }.dropWhile { it in FILLER }
        val query = words.joinToString(" ").trim().trimEnd('.', '!', '?')
        if (query.isEmpty()) return null
        return SpokenItem(amount, unit, query)
    }
}

/** How a spoken item maps onto one food's serving units. */
data class ResolvedPortion(val unit: ServingUnit, val amount: Double, val note: String?)

object VoiceResolver {

    /**
     * Picks a serving unit and amount for [item]:
     * - grams/kilos/ml/litres → the gram unit;
     * - spoons, slices, cups … → a food unit whose name contains the spoken word (e.g. "EL", "tbsp"), else grams with
     *   a standard weight (1 EL = 15 g) and a note so the user can check it;
     * - a bare count ("zwei Eier", "Banane") → the food's default unit if it is not grams, else its first non-gram
     *   unit, else 100 g per piece (flagged).
     * The amount is clamped to the unit's range.
     */
    fun resolve(item: SpokenItem, units: List<ServingUnit>): ResolvedPortion {
        val grams = units.firstOrNull { it.isGrams } ?: units.first()
        val count = item.amount ?: 1.0
        val u = item.unit
        fun clamped(unit: ServingUnit, amount: Double, note: String? = null) = ResolvedPortion(unit, Amounts.round2(Amounts.clamp(amount, unit)), note)

        if (u?.gramsPerUnit != null) return clamped(grams, count * u.gramsPerUnit)
        if (u != null && u != SpokenUnit.PIECE && u != SpokenUnit.SERVING) {
            val match = units.firstOrNull { su -> !su.isGrams && u.synonyms.any { syn -> su.name.lowercase(Locale.GERMAN).split(' ', '(', ')').contains(syn) } }
            if (match != null) return clamped(match, count)
            val g = u.fallbackGrams ?: 100.0
            return clamped(grams, count * g, "${Amounts.formatNumber(count)} × ${u.synonyms.first().uppercase(Locale.GERMAN)} ≈ ${Amounts.formatNumber(g)} g each — check")
        }
        val piece = units.firstOrNull { it.isDefault && !it.isGrams } ?: units.firstOrNull { !it.isGrams }
        if (piece != null) return clamped(piece, count)
        if (item.amount == null && u == null) return clamped(grams, Amounts.defaultAmount(grams), "no amount said — 100 g assumed")
        return clamped(grams, count * 100.0, "no piece size known — 100 g per piece assumed")
    }
}
