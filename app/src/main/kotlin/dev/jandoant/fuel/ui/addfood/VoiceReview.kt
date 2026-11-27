package dev.jandoant.fuel.ui.addfood

import dev.jandoant.fuel.core.domain.Food
import dev.jandoant.fuel.core.domain.Portion
import dev.jandoant.fuel.core.model.Amounts
import dev.jandoant.fuel.core.model.MacroNumbers
import dev.jandoant.fuel.core.model.ServingUnit
import dev.jandoant.fuel.core.model.SpokenItem
import dev.jandoant.fuel.core.model.VoiceResolver
import dev.jandoant.fuel.data.off.OffProduct
import dev.jandoant.fuel.data.off.OffResult
import dev.jandoant.fuel.data.repo.FoodRepository

/** A match candidate: a food already in Fuel, or an Open Food Facts hit (cached when chosen). */
sealed interface VoiceCandidate {
    val label: String
    data class Local(val food: Food) : VoiceCandidate { override val label get() = food.displayName }
    data class Remote(val product: OffProduct) : VoiceCandidate {
        override val label get() = if (product.brand != null) "${product.name} (${product.brand})" else product.name
    }
}

/** One spoken item in the review sheet. */
data class VoiceLine(
    val item: SpokenItem,
    val candidates: List<VoiceCandidate> = emptyList(),
    val chosen: Int = 0,
    val portion: Portion? = null,
    val note: String? = null,
    val include: Boolean = true,
    val loading: Boolean = true,
) {
    val ready: Boolean get() = include && portion != null
}

data class VoiceReview(val heard: String, val lines: List<VoiceLine>) {
    val toAdd: List<Portion> get() = lines.filter { it.ready }.mapNotNull { it.portion }
    val numbers: MacroNumbers get() = MacroNumbers.sum(toAdd.map { it.numbers })
    val loading: Boolean get() = lines.any { it.loading }
}

/** Finds foods for spoken items: your foods first (works offline), then Open Food Facts. */
class VoiceMatcher(private val foods: FoodRepository) {

    suspend fun candidates(query: String, country: String): List<VoiceCandidate> {
        for (q in queryVariants(query)) {
            val local = foods.searchLocal(q)
            if (local.isNotEmpty()) return local.take(5).map { VoiceCandidate.Local(it) }
        }
        return when (val r = foods.searchOff(query, country)) {
            is OffResult.Ok -> r.value.take(5).map { VoiceCandidate.Remote(it) }
            else -> emptyList()
        }
    }

    /** Turns the chosen candidate into a food (caching an OFF hit) and resolves the spoken amount onto its units. */
    suspend fun portion(item: SpokenItem, c: VoiceCandidate): Pair<Portion, String?> {
        val food = when (c) {
            is VoiceCandidate.Local -> c.food
            is VoiceCandidate.Remote -> foods.cache(c.product)
        }
        val r = VoiceResolver.resolve(item, food.units)
        return Portion(0, food, r.unit, r.amount) to r.note
    }

    companion object {
        /**
         * The spoken word and simple German/English singular forms ("Eier" → "Ei", "Bananen" → "Banane",
         * "Walnüsse" → "Walnuss", "apples" → "apple"), so a plural still finds the food.
         */
        fun queryVariants(q: String): List<String> {
            val w = q.trim()
            val out = linkedSetOf(w)
            when {
                w.endsWith("üsse") -> out += w.dropLast(4) + "uss"
                w.endsWith("eier") -> out += w.dropLast(2)
                w.endsWith("en") -> { out += w.dropLast(1); out += w.dropLast(2) }
                w.endsWith("es") -> { out += w.dropLast(1); out += w.dropLast(2) }
                w.endsWith("er") -> out += w.dropLast(2)
                w.endsWith("n") || w.endsWith("s") || w.endsWith("e") -> out += w.dropLast(1)
            }
            return out.filter { it.length >= 2 }
        }
    }
}

fun VoiceLine.withPortion(unit: ServingUnit, amount: Double): VoiceLine =
    copy(portion = portion?.copy(unit = unit, amount = Amounts.clamp(amount, unit)))
