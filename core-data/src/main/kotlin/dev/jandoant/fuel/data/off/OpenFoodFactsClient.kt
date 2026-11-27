package dev.jandoant.fuel.data.off

import dev.jandoant.fuel.core.model.Nutrient
import dev.jandoant.fuel.core.model.Nutrients
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A product as Fuel needs it. Mineral/vitamin values are converted from OFF's grams to mg / µg. */
data class OffProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val per100g: Nutrients,
    /** Serving size in grams, if Open Food Facts has one. */
    val servingGrams: Double?,
    val servingLabel: String?,
    /** Package size in grams (e.g. a 500 g tub). */
    val packageGrams: Double?,
)

sealed interface OffResult<out T> {
    data class Ok<T>(val value: T) : OffResult<T>
    data object NotFound : OffResult<Nothing>
    data class Error(val message: String) : OffResult<Nothing>
}

/**
 * Open Food Facts API v2 (PRD §4.3): custom User-Agent, German names preferred, simple client-side rate limits
 * (product reads ≤ 100/min, search ≤ 10/min). The UI also debounces search input.
 */
class OpenFoodFactsClient(
    private val http: HttpClient = defaultClient(),
    private val baseUrl: String = "https://world.openfoodfacts.org",
    private val searchUrl: String = "https://search.openfoodfacts.org",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val productLimiter = RateLimiter(100, 60_000, clock)
    private val searchLimiter = RateLimiter(10, 60_000, clock)

    suspend fun product(barcode: String, country: String = "de"): OffResult<OffProduct> = runCatchingOff {
        productLimiter.acquire() ?: return OffResult.Error("Too many lookups — try again in a minute")
        val resp = http.get("$baseUrl/api/v2/product/$barcode") {
            parameter("fields", FIELDS)
            parameter("lc", country)
            parameter("cc", country)
        }
        if (resp.status == HttpStatusCode.NotFound) return OffResult.NotFound
        if (resp.status != HttpStatusCode.OK) return OffResult.Error("Open Food Facts: HTTP ${resp.status.value}")
        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        val status = root["status"]?.jsonPrimitive?.content
        val product = root["product"]?.jsonObject
        if (status == "0" || product == null) return OffResult.NotFound
        parseProduct(product, country)?.let { OffResult.Ok(it) } ?: OffResult.NotFound
    }

    /**
     * Text search via Open Food Facts' search service (search-a-licious). The legacy `/cgi/search.pl` endpoint is
     * only used as a fallback — it is frequently throttled and answers 503.
     */
    suspend fun search(query: String, country: String = "de", pageSize: Int = 20): OffResult<List<OffProduct>> = runCatchingOff {
        searchLimiter.acquire() ?: return OffResult.Error("Search limit reached — try again in a minute")
        val resp = http.get("$searchUrl/search") {
            parameter("q", query)
            parameter("page_size", pageSize)
            parameter("langs", if (country == "world") "en" else "$country,en")
            parameter("fields", FIELDS)
        }
        if (resp.status == HttpStatusCode.OK) {
            val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val hits = root["hits"]?.jsonArray.orEmpty().mapNotNull { (it as? JsonObject)?.let { p -> parseProduct(p, country) } }
            return OffResult.Ok(hits)
        }
        val legacy = http.get("$baseUrl/cgi/search.pl") {
            parameter("search_terms", query)
            parameter("search_simple", 1)
            parameter("action", "process")
            parameter("json", 1)
            parameter("page_size", pageSize)
            parameter("fields", FIELDS)
            parameter("lc", country)
            parameter("cc", country)
        }
        if (legacy.status != HttpStatusCode.OK) {
            return OffResult.Error("Open Food Facts search is unavailable right now (HTTP ${resp.status.value}). Barcode scanning still works.")
        }
        val root = json.parseToJsonElement(legacy.bodyAsText()).jsonObject
        OffResult.Ok(root["products"]?.jsonArray.orEmpty().mapNotNull { (it as? JsonObject)?.let { p -> parseProduct(p, country) } })
    }

    private inline fun <T> runCatchingOff(block: () -> OffResult<T>): OffResult<T> = try {
        block()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        OffResult.Error(e.message ?: e::class.simpleName ?: "Network error")
    }

    companion object {
        /** No personal contact data is sent; see docs/decisions.md. */
        const val USER_AGENT = "Fuel/1.0 (personal Android app, non-commercial)"

        const val FIELDS = "code,product_name,product_name_de,product_name_en,generic_name,brands,nutriments," +
            "serving_size,serving_quantity,product_quantity,quantity"

        val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun defaultClient() = HttpClient(OkHttp) {
            install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 10_000 }
            defaultRequest { header(HttpHeaders.UserAgent, USER_AGENT) }
            expectSuccess = false
        }

        /** OFF nutriment key → (nutrient, factor from OFF's unit to ours). */
        private val NUTRIMENTS: List<Triple<String, Nutrient, Double>> = listOf(
            Triple("energy-kcal_100g", Nutrient.ENERGY, 1.0),
            Triple("proteins_100g", Nutrient.PROTEIN, 1.0),
            Triple("fat_100g", Nutrient.FAT, 1.0),
            Triple("saturated-fat_100g", Nutrient.SAT_FAT, 1.0),
            Triple("carbohydrates_100g", Nutrient.CARBS, 1.0),
            Triple("sugars_100g", Nutrient.SUGAR, 1.0),
            Triple("fiber_100g", Nutrient.FIBER, 1.0),
            Triple("salt_100g", Nutrient.SALT, 1.0),
            Triple("potassium_100g", Nutrient.POTASSIUM, 1000.0),
            Triple("calcium_100g", Nutrient.CALCIUM, 1000.0),
            Triple("magnesium_100g", Nutrient.MAGNESIUM, 1000.0),
            Triple("iron_100g", Nutrient.IRON, 1000.0),
            Triple("zinc_100g", Nutrient.ZINC, 1000.0),
            Triple("vitamin-c_100g", Nutrient.VITAMIN_C, 1000.0),
            Triple("vitamin-d_100g", Nutrient.VITAMIN_D, 1_000_000.0),
            Triple("vitamin-b12_100g", Nutrient.VITAMIN_B12, 1_000_000.0),
            Triple("vitamin-b9_100g", Nutrient.FOLATE, 1_000_000.0),
            Triple("folates_100g", Nutrient.FOLATE, 1_000_000.0),
        )

        fun parseProduct(p: JsonObject, country: String = "de"): OffProduct? {
            val code = p.str("code") ?: return null
            val name = listOfNotNull(
                p.str("product_name_$country"), p.str("product_name"), p.str("product_name_en"), p.str("generic_name"),
            ).firstOrNull { it.isNotBlank() } ?: return null
            val n = p["nutriments"] as? JsonObject
            val values = mutableMapOf<Nutrient, Double>()
            if (n != null) {
                for ((key, nutrient, factor) in NUTRIMENTS) {
                    if (values.containsKey(nutrient)) continue
                    n.num(key)?.let { values[nutrient] = it * factor }
                }
                // Some products only have kJ.
                if (!values.containsKey(Nutrient.ENERGY)) n.num("energy_100g")?.let { values[Nutrient.ENERGY] = it / 4.184 }
            }
            return OffProduct(
                barcode = code,
                name = name.trim(),
                // "brands" is a comma-separated string in the product API and an array in the search service.
                brand = ((p["brands"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.content ?: p.str("brands")?.split(',')?.firstOrNull())
                    ?.trim()?.takeIf { it.isNotBlank() },
                per100g = Nutrients(values),
                servingGrams = p.num("serving_quantity")?.takeIf { it > 0 },
                servingLabel = p.str("serving_size"),
                packageGrams = p.num("product_quantity")?.takeIf { it > 0 },
            )
        }

        private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
        private fun JsonObject.num(k: String): Double? = (this[k] as? JsonElement)?.let { e ->
            (e as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.replace(',', '.').toDoubleOrNull() }
        }
    }
}

/** Allows [max] calls per [windowMs]; returns null instead of waiting when the budget is used up. */
class RateLimiter(private val max: Int, private val windowMs: Long, private val clock: () -> Long) {
    private val mutex = Mutex()
    private val calls = ArrayDeque<Long>()

    suspend fun acquire(): Unit? = mutex.withLock {
        val now = clock()
        while (calls.isNotEmpty() && now - calls.first() >= windowMs) calls.removeFirst()
        if (calls.size >= max) return@withLock null
        calls.addLast(now)
        Unit
    }
}
