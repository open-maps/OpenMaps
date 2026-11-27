package nz.eloque.openmaps.data.flights

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nz.eloque.openmaps.core.model.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

// Trimmed from a real answer of api.adsb.lol, plus a plane on the ground and one without a position.
private const val AIRCRAFT =
    """{"ac":[
    {"hex":"89655f","type":"adsb_icao","flight":"UAE6    ","r":"A6-EVS","t":"A388","alt_baro":37000,"gs":540.4,"track":127.10,
    "baro_rate":-640,"lat":48.933850,"lon":8.423261,"seen_pos":0.315},
    {"hex":"~3c6444","flight":"DLH400  ","t":"B748","alt_baro":"ground","gs":12.0,"track":90.0,"lat":50.03,"lon":8.57,"seen_pos":2.0},
    {"hex":"4ca87c","flight":"RYR1   ","seen":3.0}
    ],"now":1795736295011,"total":3}"""

private const val ROUTES =
    "Callsign,Code,Number,AirlineCode,AirportCodes\nDLH400,DLH,400,DLH,EDDF-KJFK\nDLH402,DLH,402,DLH,EDDF-EDDH-KJFK\n"

private const val AIRPORT_HEADER = "Code,Name,ICAO,IATA,Location,CountryISO2,Latitude,Longitude,AltitudeFeet\n"
private const val EDDF =
    AIRPORT_HEADER + "EDDF,Frankfurt am Main Airport,EDDF,FRA,Frankfurt,DE,50.0333,8.5706,364\n" +
        "EDDH,\"Hamburg Airport, Helmut Schmidt\",EDDH,HAM,Hamburg,DE,53.6304,9.9882,53\n"
private const val KJFK = AIRPORT_HEADER + "KJFK,John F Kennedy International Airport,KJFK,JFK,New York,US,40.6398,-73.7789,13\n"
private const val AIRLINES =
    "Code,Name,ICAO,IATA,PositioningFlightPattern,CharterFlightPattern\nLH,Lufthansa,DLH,LH,,\n" +
        "AHE,\"Airport Helicopter Basel, Muller &amp; Co.\",AHE,,,\n"
private const val MODELS = "ICAO,Manufacturer,Model,Engines\nB748,Boeing,747-8,4\n"

class FlightSourcesTest {
    private val requests = mutableListOf<String>()

    private val client =
        HttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                requests += path
                val (body, type) =
                    when {
                        path.startsWith("/v2/") -> AIRCRAFT to "application/json"
                        path.endsWith("/D/DLH-all.csv") -> ROUTES to "text/plain"
                        path.endsWith("/E/ED.csv") -> EDDF to "text/plain"
                        path.endsWith("/K/KJ.csv") -> KJFK to "text/plain"
                        path.endsWith("/airlines.csv") -> AIRLINES to "text/plain"
                        path.endsWith("/model-type/schema-01/B.csv") -> MODELS to "text/plain"
                        else -> return@MockEngine respond("", HttpStatusCode.NotFound)
                    }
                respond(body, headers = headersOf(HttpHeaders.ContentType, type))
            },
        ) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    private val sources =
        FlightSources(AdsbLol(client, flowOf("https://adsb.example/")), StandingData(client, flowOf("https://data.example")))

    @Test
    fun aircraftComeInMetricUnitsWithTheAgeOfTheirPosition() =
        runTest {
            val aircraft = sources.near(LatLng(49.0, 8.4), 100_000.0)

            assertEquals("/v2/point/49.0/8.4/54", requests.single())
            assertEquals(2, aircraft.size, "A plane without a position is left out")
            val a380 = aircraft[0]
            assertEquals("UAE6", a380.callsign)
            assertEquals(11277.6, a380.altitudeMeters!!, 0.01)
            assertEquals(278.0, a380.groundSpeedMetersPerSecond!!, 0.1)
            assertEquals(-3.25, a380.verticalRateMetersPerSecond!!, 0.01)
            assertEquals(Instant.fromEpochMilliseconds(1795736295011 - 315), a380.positionTime)
            val parked = aircraft[1]
            assertEquals("3c6444", parked.id)
            assertTrue(parked.onGround)
        }

    @Test
    fun aFlightIsNamedAndPlacedOnTheLegItIsFlying() =
        runTest {
            val overHamburg = sources.near(LatLng(0.0, 0.0), 1.0)[1].copy(callsign = "DLH402", location = LatLng(55.0, 0.0))

            val info = sources.info(overHamburg)

            assertEquals("Lufthansa", info.airline)
            assertEquals("Boeing 747-8", info.model)
            assertEquals("HAM", info.origin?.iata)
            assertEquals("Hamburg Airport, Helmut Schmidt", info.origin?.name)
            assertEquals("JFK", info.destination?.iata)
        }

    @Test
    fun privateFlightsAndUnknownFilesHaveNoRoute() =
        runTest {
            val plane = sources.near(LatLng(0.0, 0.0), 1.0)[0]

            assertNull(sources.info(plane.copy(callsign = "DEABC")).airline)
            val unknown = sources.info(plane)
            assertNull(unknown.origin)
            sources.info(plane)
            assertEquals(1, requests.count { it.endsWith("UAE-all.csv") }, "A missing file is asked for once")
        }

    @Test
    fun tablesKeepQuotedCommasAndDecodeEntities() {
        val airlines = parseTable(AIRLINES, key = "ICAO")

        assertEquals("Airport Helicopter Basel, Muller & Co.", airlines.getValue("AHE")["Name"])
    }
}
