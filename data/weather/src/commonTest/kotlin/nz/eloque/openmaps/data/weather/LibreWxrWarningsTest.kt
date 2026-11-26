package nz.eloque.openmaps.data.weather

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.WarningSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

// Trimmed from real answers of api.librewxr.net, with a second warning that has already expired.
private const val SALZBURG =
    """{"type":"FeatureCollection","features":[
    {"type":"Feature","properties":{"title":"Windwarnung","severity":"Moderate","time":1794910242,"expires":1795014000,
    "description":"Starker Wind mit Spitzen bis 80 km/h ist zu erwarten.","regions":["Salzburg (Stadt)"],
    "uri":"https://feeds.meteoalarm.org/api/v1/warnings/feeds-austria/3c308d03-11a3-4c43-bc4b-d4e4023cb10a"},
    "geometry":{"type":"Polygon","coordinates":[[[13.06,47.84],[13.01,47.75],[13.12,47.80],[13.06,47.84]]]}},
    {"type":"Feature","properties":{"title":"Gewitter","severity":"Severe","time":1794800000,"expires":1794900000,
    "description":"Gewitter","regions":["A; B"],"uri":"urn:oid:2.49.0.1.40.0"},
    "geometry":{"type":"Polygon","coordinates":[]}},
    {"type":"Feature","properties":{"title":"Sturm","severity":"Severe","time":1794950000,"expires":null,
    "description":"","regions":["Salzburg; Flachgau; Salzburg"],"uri":"urn:oid:2.49.0.1.40.1"},
    "geometry":{"type":"Polygon","coordinates":[]}}]}"""

private object FixedClock : Clock {
    override fun now() = Instant.fromEpochSeconds(1794960000)
}

class LibreWxrWarningsTest {
    @Test
    fun warningsInForceComeMostSevereFirst() =
        runTest {
            var requests = 0
            val client =
                HttpClient(
                    MockEngine { request ->
                        requests++
                        assertEquals("/v2/alerts", request.url.encodedPath)
                        assertEquals("47.8", request.url.parameters["lat"])
                        respond(SALZBURG, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            val warnings = LibreWxrWarnings(client, flowOf("https://wxr.example/"), FixedClock)

            val found = warnings.at(LatLng(47.8, 13.04))
            assertEquals(listOf("Sturm", "Windwarnung"), found.map { it.title })
            assertEquals(WarningSeverity.SEVERE, found[0].severity)
            assertEquals(listOf("Salzburg", "Flachgau"), found[0].regions)
            assertEquals(null, found[0].description)
            assertEquals(null, found[0].source)
            assertEquals("meteoalarm.org", found[1].source)
            assertEquals(Instant.fromEpochSeconds(1795014000), found[1].end)

            warnings.at(LatLng(47.8001, 13.0401))
            assertEquals(1, requests, "a place next door is answered from the cache")
        }

    @Test
    fun aWarningIssuedInTwoLanguagesShowsOnceInTheDeviceLanguage() {
        val id = "2.49.0.0.276.0.DWD.PVW.1795713540000.4e2826aa"
        val copies =
            listOf(
                AlertDto(title = "Official WARNING of ICY SURFACES", uri = "https://www.dwd.de/DWD/warnungen/cap-feed/en/$id.ENG.xml"),
                AlertDto(title = "Amtliche WARNUNG vor GLÄTTE", uri = "https://www.dwd.de/DWD/warnungen/cap-feed/de/$id.DEU.xml"),
                AlertDto(title = "Windwarnung", uri = "https://feeds.meteoalarm.org/api/v1/warnings/feeds-austria/3c308d03"),
            )
        assertEquals(listOf("Amtliche WARNUNG vor GLÄTTE", "Windwarnung"), copies.inLanguage("de").map { it.title })
        assertEquals(listOf("Official WARNING of ICY SURFACES", "Windwarnung"), copies.inLanguage("fr").map { it.title })
    }

    @Test
    fun noServerMeansNoWarnings() =
        runTest {
            val client = HttpClient(MockEngine { error("no request expected") })
            assertTrue(LibreWxrWarnings(client, flowOf(null)).at(LatLng(47.8, 13.04)).isEmpty())
        }
}
