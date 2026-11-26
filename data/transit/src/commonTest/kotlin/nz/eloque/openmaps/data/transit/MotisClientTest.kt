package nz.eloque.openmaps.data.transit

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.ModeGroup
import nz.eloque.openmaps.core.model.PlaceId
import nz.eloque.openmaps.core.model.PlanRequest
import nz.eloque.openmaps.core.model.PlanTarget
import nz.eloque.openmaps.core.model.RouteOptions
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.WalkingSpeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class MotisClientTest {
    private val requests = mutableListOf<Url>()

    private fun client(body: String) =
        HttpClient(
            MockEngine { request ->
                requests += request.url
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    @Test
    fun geocodeMapsPlacesStopsAndOsmIds() =
        runTest {
            val geocoder = MotisGeocoder(client(GEOCODE_JSON), { TRANSITOUS_API }) { "nl" }
            val results = geocoder.search("Rijksmuseum", near = LatLng(52.36, 4.89))

            val place = assertIs<SearchResult.Place>(results.first())
            assertEquals(PlaceId("osm:way/29989787"), place.id)
            assertEquals("Amsterdam", place.area)
            assertTrue(results.any { it is SearchResult.Stop && TransportMode.TRAM in it.modes })
            assertEquals("52.36,4.89", requests.single().parameters["place"])
            assertEquals("3", requests.single().parameters["placeBias"])
            assertEquals("nl", requests.single().parameters["language"])
        }

    @Test
    fun stopsFirstMovesStopsToTheTop() =
        runTest {
            val geocoder = MotisGeocoder(client(GEOCODE_JSON), { TRANSITOUS_API }) { null }
            val results = geocoder.search("Rijksmuseum", near = null, stopsFirst = true)
            assertIs<SearchResult.Stop>(results.first())
        }

    @Test
    fun planMapsWalkAndTransitLegs() =
        runTest {
            val repository = MotisTransitRepository(client(PLAN_JSON), { TRANSITOUS_API })
            val result =
                repository.plan(
                    PlanRequest(
                        from = PlanTarget.Coordinates(LatLng(52.3596918, 4.8846391)),
                        to = PlanTarget.Stop("de-DELFI_000008400058"),
                        time = null,
                    ),
                )

            val legs = result.itineraries.single().legs
            assertIs<Leg.Street>(legs[0])
            val bus = assertIs<Leg.Transit>(legs[1])
            assertEquals("391", bus.line.label)
            assertEquals(TransportMode.BUS, bus.mode)
            assertEquals(0xFFE1251B, bus.line.color)
            assertEquals(false, bus.isWheelchairAccessible)
            assertTrue(bus.geometry.size > 1)
            assertEquals("de-DELFI_000008400058", requests.single().parameters["toPlace"])
        }

    @Test
    fun rebuildsAnItineraryUnderItsOriginalId() =
        runTest {
            val itinerary = Json.parseToJsonElement(PLAN_JSON).jsonObject.getValue("itineraries").jsonArray[0].toString()
            val repository = MotisTransitRepository(client(itinerary), { TRANSITOUS_API })

            val rebuilt = repository.itinerary("saved-id")
            assertEquals("saved-id", rebuilt?.id)
            assertEquals("saved-id", requests.single().parameters["itineraryId"])
            assertTrue(rebuilt!!.legs.any { it is Leg.Transit })
        }

    @Test
    fun itinerariesTheServiceCannotRebuildAreNull() =
        runTest {
            val failing =
                HttpClient(MockEngine { respond("""{"error":"x"}""", HttpStatusCode.BadRequest) }) {
                    expectSuccess = true
                }
            assertNull(MotisTransitRepository(failing, { TRANSITOUS_API }).itinerary("gone"))
        }

    @Test
    fun departuresKeepScheduledAndRealtimeTimes() =
        runTest {
            val repository = MotisTransitRepository(client(STOP_TIMES_JSON), { TRANSITOUS_API })
            val result = repository.departures("de-DELFI_000008400058")

            val intercity = result.departures.first()
            assertEquals("Intercity", intercity.line.label)
            assertEquals(8.minutes, intercity.time.delay)
            assertEquals("2a", intercity.platform)
            assertTrue(intercity.time.isRealTime)
            assertNull(requests.single().parameters["language"])
        }

    @Test
    fun arrivalsAreAskedForFromTheStartTimeOnwards() =
        runTest {
            val result =
                MotisTransitRepository(client(STOP_TIMES_JSON), { TRANSITOUS_API })
                    .departures("de-DELFI_000008400058", arrivals = true)
            val parameters = requests.single().parameters
            assertEquals("true", parameters["arriveBy"])
            assertEquals("LATER", parameters["direction"])
            assertEquals("LATER|1794604560", result.laterCursor)
        }

    @Test
    fun timetablesAreAskedForInTheDeviceLanguage() =
        runTest {
            MotisTransitRepository(client(STOP_TIMES_JSON), { TRANSITOUS_API }) { "fr" }.departures("be-sncb_S8813003")
            MotisTransitRepository(client(PLAN_JSON), { TRANSITOUS_API }) { "fr" }
                .plan(PlanRequest(PlanTarget.Stop("a"), PlanTarget.Stop("b"), time = null))
            assertEquals(listOf("fr", "fr"), requests.map { it.parameters["language"] })
        }

    @Test
    fun stopsInSendsCornersAndMapsModes() =
        runTest {
            val body =
                """[{"name":"Karlsruhe Barbarossaplatz","stopId":"de-KVV_Pde:08212:5003","lat":48.9949,"lon":8.3896,
                "tz":"Europe/Berlin","vertexType":"TRANSIT","modes":["TRAM","BUS"]},{"name":"No id","lat":1.0,"lon":2.0}]"""
            val repository = MotisTransitRepository(client(body), { TRANSITOUS_API })
            val stops = repository.stopsIn(GeoBounds(south = 48.99, west = 8.385, north = 49.002, east = 8.41))

            assertEquals(setOf(TransportMode.TRAM, TransportMode.BUS), stops.single().modes)
            assertEquals("48.99,8.41", requests.single().parameters["min"])
            assertEquals("49.002,8.385", requests.single().parameters["max"])
        }

    @Test
    fun planSendsRouteOptions() =
        runTest {
            val repository = MotisTransitRepository(client(PLAN_JSON), { TRANSITOUS_API })
            val target = PlanTarget.Coordinates(LatLng(1.0, 2.0))
            repository.plan(PlanRequest(target, target, time = null))
            repository.plan(
                PlanRequest(
                    target,
                    target,
                    time = null,
                    options =
                        RouteOptions(
                            modes = setOf(ModeGroup.LONG_DISTANCE_TRAIN, ModeGroup.BUS),
                            maxTransfers = 0,
                            walkingSpeed = WalkingSpeed.SLOW,
                            wheelchair = true,
                            bikeOnBoard = true,
                            avoidReservations = true,
                        ),
                ),
            )

            val defaults = requests[0].parameters
            assertEquals(null, defaults["transitModes"])
            assertEquals(null, defaults["maxTransfers"])
            assertEquals(null, defaults["pedestrianProfile"])
            assertEquals(null, defaults["requireBikeTransport"])
            assertEquals(null, defaults["noCompulsoryReservation"])
            assertEquals("false", defaults["joinInterlinedLegs"])
            assertEquals("3", defaults["numLegAlternatives"])
            val custom = requests[1].parameters
            assertEquals("HIGHSPEED_RAIL,LONG_DISTANCE,NIGHT_RAIL,BUS,COACH,FUNICULAR,AERIAL_LIFT,OTHER", custom["transitModes"])
            assertEquals("0", custom["maxTransfers"])
            assertEquals("0.9", custom["pedestrianSpeed"])
            assertEquals("WHEELCHAIR", custom["pedestrianProfile"])
            assertEquals("true", custom["requireBikeTransport"])
            assertEquals("true", custom["noCompulsoryReservation"])
            assertEquals(null, custom["useRoutedTransfers"], "Transitous finds almost no wheelchair routes with routed transfers")
        }

    @Test
    fun streetOnlyPlanSkipsTransitAndAllowsLongDirectRoutes() =
        runTest {
            val repository = MotisTransitRepository(client(PLAN_JSON), { TRANSITOUS_API })
            val target = PlanTarget.Coordinates(LatLng(1.0, 2.0))
            repository.plan(PlanRequest(target, target, time = null))
            repository.plan(
                PlanRequest(
                    target,
                    target,
                    time = null,
                    options = RouteOptions(modes = setOf(ModeGroup.BUS)),
                    directModes = setOf(TransportMode.CAR, TransportMode.BIKE),
                    withTransit = false,
                ),
            )

            val transit = requests[0].parameters
            assertEquals("WALK", transit["directModes"])
            assertEquals(null, transit["maxDirectTime"])
            val street = requests[1].parameters
            assertEquals("CAR,BIKE", street["directModes"])
            assertEquals(listOf(""), street.getAll("transitModes"))
            assertEquals("10800", street["maxDirectTime"])
        }

    @Test
    fun mainStreetsAreTheLongestInTravelOrder() {
        val steps =
            listOf(
                StepDto("", 20.0),
                StepDto("Vorholzstraße", 50.0),
                StepDto("Hirschstraße", 10.0),
                StepDto("Kriegsstraße", 900.0),
                StepDto("Vorholzstraße", 60.0),
            )
        assertEquals(listOf("Vorholzstraße", "Kriegsstraße"), mainStreets(steps))
    }

    @Test
    fun timesAreSentInWholeSeconds() =
        runTest {
            val repository = MotisTransitRepository(client(STOP_TIMES_JSON), { TRANSITOUS_API })
            repository.departures("stop", time = Instant.parse("2026-11-14T15:42:25.123456Z"))
            assertEquals("2026-11-14T15:42:25Z", requests.single().parameters["time"])
        }

    @Test
    fun alertKeepsFirstTranslation() {
        val alert = AlertDto(headerText = "Werkzaamheden -|Construction works").toAlert()
        assertEquals("Werkzaamheden", alert?.header)
    }

    @Test
    fun unknownModeFallsBackToOther() {
        assertEquals(TransportMode.OTHER, transportMode("HOVERCRAFT"))
    }

    @Test
    fun linesAtAStopLeaveOutSingleTrainsAndPutRailFirst() =
        runTest {
            val body =
                """{"routes":[
                {"mode":"BUS","routeShortName":"S5E"},{"mode":"BUS","routeShortName":"S5E","agencyName":"Other"},
                {"mode":"HIGHSPEED_RAIL","routeShortName":"11"},{"mode":"REGIONAL_RAIL","routeShortName":"EN 40237"},
                {"mode":"SUBURBAN","routeShortName":"S11"},{"mode":"SUBURBAN","routeShortName":"S2"},
                {"mode":"TRAM","routeShortName":""},{"mode":"BUS","routeShortName":"40"}]}"""
            val repository = MotisTransitRepository(client(body), { TRANSITOUS_API })
            assertEquals(listOf("S2", "S11", "40", "S5E"), repository.lines("stop").map { it.label })
            assertEquals("stop", requests.single().parameters["stopId"])
        }
}
