package dev.jandoant.fuel.data

import dev.jandoant.fuel.core.model.Nutrient
import dev.jandoant.fuel.core.model.UnitSource
import dev.jandoant.fuel.data.off.OffResult
import dev.jandoant.fuel.data.off.OpenFoodFactsClient
import dev.jandoant.fuel.data.off.RateLimiter
import dev.jandoant.fuel.data.repo.FoodRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenFoodFactsTest {
    private val skyr = """
        {"code":"4056489012345","status":1,"product":{"code":"4056489012345","product_name":"Skyr","product_name_de":"Skyr natur",
         "brands":"Milbona, Lidl","serving_quantity":"150","product_quantity":500,
         "nutriments":{"energy-kcal_100g":63,"proteins_100g":11,"fat_100g":0.2,"carbohydrates_100g":4,"sugars_100g":4,
         "salt_100g":"0,1","calcium_100g":0.12,"vitamin-d_100g":0.0000015}}}
    """.trimIndent()

    @Test fun `parses German name, brand, nutrients and units`() {
        val root = OpenFoodFactsClient.json.parseToJsonElement(skyr).jsonObject
        val p = OpenFoodFactsClient.parseProduct(root["product"]!!.jsonObject, "de")!!
        assertEquals("Skyr natur", p.name)
        assertEquals("Milbona", p.brand)
        assertEquals(63.0, p.per100g[Nutrient.ENERGY])
        assertEquals(0.1, p.per100g[Nutrient.SALT], 1e-9) // comma decimal
        assertEquals(120.0, p.per100g[Nutrient.CALCIUM], 1e-9) // g → mg
        assertEquals(1.5, p.per100g[Nutrient.VITAMIN_D], 1e-9) // g → µg
        assertFalse(p.per100g.has(Nutrient.FIBER)) // missing stays missing
        assertEquals(150.0, p.servingGrams)
        assertEquals(500.0, p.packageGrams)
    }

    @Test fun `default units are grams, serving as default, package`() {
        val root = OpenFoodFactsClient.json.parseToJsonElement(skyr).jsonObject
        val units = FoodRepository.defaultUnitsFor(OpenFoodFactsClient.parseProduct(root["product"]!!.jsonObject)!!)
        assertEquals(listOf("g", "serving (150 g)", "package (500 g)"), units.map { it.name })
        assertEquals(UnitSource.BUILT_IN, units[0].source)
        assertTrue(units[1].isDefault)
        assertFalse(units[0].isDefault)
    }

    @Test fun `kJ only products get kcal`() {
        val root = OpenFoodFactsClient.json.parseToJsonElement("""{"code":"1","product_name":"X","nutriments":{"energy_100g":418.4}}""").jsonObject
        assertEquals(100.0, OpenFoodFactsClient.parseProduct(root)!!.per100g[Nutrient.ENERGY], 1e-9)
    }

    @Test fun `product lookup sends a custom user agent and handles not found`() = runTest {
        var agent: String? = null
        val engine = MockEngine { req ->
            agent = req.headers[HttpHeaders.UserAgent]
            if (req.url.encodedPath.endsWith("/4056489012345")) respond(skyr, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            else respond("""{"status":0}""", HttpStatusCode.OK)
        }
        val client = OpenFoodFactsClient(HttpClient(engine) { install(io.ktor.client.plugins.DefaultRequest) { headers.append(HttpHeaders.UserAgent, OpenFoodFactsClient.USER_AGENT) } })
        val ok = client.product("4056489012345")
        assertIs<OffResult.Ok<*>>(ok)
        assertNotNull(agent)
        assertTrue(agent!!.startsWith("Fuel/1.0"))
        assertEquals(OffResult.NotFound, client.product("000"))
    }

    @Test fun `search uses the search service and falls back to the legacy endpoint`() = runTest {
        val paths = mutableListOf<String>()
        val hits = """{"hits":[{"code":"1","product_name":"Skyr citron","brands":["Lidl"],"nutriments":{"energy-kcal_100g":89}}]}"""
        val engine = MockEngine { req ->
            paths += req.url.host + req.url.encodedPath
            when {
                req.url.host == "search.openfoodfacts.org" -> respond(hits, HttpStatusCode.OK)
                else -> respond("", HttpStatusCode.ServiceUnavailable)
            }
        }
        val r = OpenFoodFactsClient(HttpClient(engine)).search("skyr")
        assertIs<OffResult.Ok<*>>(r)
        @Suppress("UNCHECKED_CAST") val list = (r as OffResult.Ok<List<dev.jandoant.fuel.data.off.OffProduct>>).value
        assertEquals("Lidl", list.single().brand)
        assertEquals(listOf("search.openfoodfacts.org/search"), paths)

        val down = OpenFoodFactsClient(HttpClient(MockEngine { respond("", HttpStatusCode.ServiceUnavailable) })).search("skyr")
        assertIs<OffResult.Error>(down)
        assertTrue(down.message.contains("503"))
    }

    /** Real shapes from 2026-11-27: the search index returned "Olivenöl" hits without nutriments. */
    @Test fun `search hits without nutriments are completed from the product API, never shown as 0 kcal`() = runTest {
        val search = """{"hits":[
            {"code":"4056489640981","product_name":"Olivenöl","brands":["Lidl"]},
            {"code":"0000000000001","product_name":"Olivenöl ohne Daten"}]}"""
        val product = """{"code":"4056489640981","status":1,"product":{"code":"4056489640981","product_name_de":"Natives Olivenöl extra",
            "nutriments":{"energy":3447,"energy-kcal":824,"energy-kcal_100g":824,"energy_100g":3447,"fat_100g":91.6,
            "saturated-fat_100g":14,"carbohydrates_100g":0,"sugars_100g":0,"proteins_100g":0,"salt_100g":0}}}"""
        val engine = MockEngine { req ->
            when {
                req.url.host == "search.openfoodfacts.org" -> respond(search, HttpStatusCode.OK)
                req.url.encodedPath.endsWith("/4056489640981") -> respond(product, HttpStatusCode.OK)
                else -> respond("""{"status":0}""", HttpStatusCode.OK)
            }
        }
        @Suppress("UNCHECKED_CAST")
        val list = (OpenFoodFactsClient(HttpClient(engine)).search("Olivenöl") as OffResult.Ok<List<dev.jandoant.fuel.data.off.OffProduct>>).value
        assertEquals(824.0, list[0].per100g[Nutrient.ENERGY])
        assertEquals(91.6, list[0].per100g[Nutrient.FAT])
        assertTrue(list[0].hasNutrition)
        assertFalse(list[1].hasNutrition) // stays in the list, flagged — not 0 kcal
        assertFalse(list[1].per100g.has(Nutrient.ENERGY))
    }

    @Test fun `rate limiter refuses beyond the budget and recovers after the window`() = runTest {
        var now = 0L
        val rl = RateLimiter(2, 1000) { now }
        assertNotNull(rl.acquire())
        assertNotNull(rl.acquire())
        assertNull(rl.acquire())
        now = 1000
        assertNotNull(rl.acquire())
    }
}
