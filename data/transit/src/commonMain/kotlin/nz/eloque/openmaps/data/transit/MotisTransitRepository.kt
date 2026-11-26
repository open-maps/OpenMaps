package nz.eloque.openmaps.data.transit

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import nz.eloque.openmaps.core.model.DeparturesResult
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.Line
import nz.eloque.openmaps.core.model.ModeGroup
import nz.eloque.openmaps.core.model.PlanRequest
import nz.eloque.openmaps.core.model.PlanResult
import nz.eloque.openmaps.core.model.PlanTarget
import nz.eloque.openmaps.core.model.RouteOptions
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.TransitRepository
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.VehicleKind
import nz.eloque.openmaps.core.model.WalkingSpeed
import kotlin.time.Instant

internal class MotisTransitRepository(
    private val client: HttpClient,
    private val baseUrl: () -> String,
    private val language: () -> String? = { null },
) : TransitRepository {
    override suspend fun stopsIn(bounds: GeoBounds): List<SearchResult.Stop> {
        val stops: List<PlaceDto> =
            client
                .get("${baseUrl()}/v6/map/stops") {
                    parameter("min", "${bounds.south},${bounds.east}")
                    parameter("max", "${bounds.north},${bounds.west}")
                    parameter("grouped", true)
                }.body()
        return stops.mapNotNull { stop ->
            val id = stop.stopId ?: return@mapNotNull null
            SearchResult.Stop(id, stop.name, LatLng(stop.lat, stop.lon), null, stop.modes.map(::transportMode).toSet())
        }
    }

    override suspend fun plan(request: PlanRequest): PlanResult {
        val response: PlanResponseDto =
            client
                .get("${baseUrl()}/v6/plan") {
                    parameter("fromPlace", request.from.asParameter())
                    parameter("toPlace", request.to.asParameter())
                    request.time?.let { parameter("time", it.asParameter()) }
                    parameter("arriveBy", request.arriveBy)
                    val direct =
                        request.directModes.map { it.name } + if (request.directSharing.isEmpty()) emptyList() else listOf("RENTAL")
                    parameter("directModes", direct.joinToString(","))
                    if (request.directSharing.isNotEmpty()) {
                        parameter("directRentalFormFactors", request.directSharing.flatMap(::formFactors).joinToString(","))
                        parameter("maxDirectTime", MAX_SHARED_DIRECT_SECONDS)
                    } else if (request.directModes.any { it != TransportMode.WALK } || !request.withTransit) {
                        parameter("maxDirectTime", MAX_DIRECT_SECONDS)
                    }
                    request.pageCursor?.let { parameter("pageCursor", it) }
                    parameter("joinInterlinedLegs", false)
                    inLanguage()
                    if (request.withTransit) parameter("numLegAlternatives", LEG_ALTERNATIVES)
                    routeOptions(request.options, request.withTransit)
                }.body()
        return PlanResult(
            itineraries = response.itineraries.map { it.toItinerary() },
            direct = response.direct.map { it.toItinerary() },
            earlierCursor = response.previousPageCursor,
            laterCursor = response.nextPageCursor,
        )
    }

    override suspend fun trip(tripId: String): Itinerary {
        val response: ItineraryDto =
            client
                .get("${baseUrl()}/v6/trip") {
                    parameter("tripId", tripId)
                    parameter("joinInterlinedLegs", false)
                    inLanguage()
                }.body()
        return response.toItinerary()
    }

    override suspend fun itinerary(id: String): Itinerary? =
        try {
            client
                .get("${baseUrl()}/v6/refresh-itinerary") {
                    parameter("itineraryId", id)
                    parameter("joinInterlinedLegs", false)
                    inLanguage()
                    parameter("numLegAlternatives", LEG_ALTERNATIVES)
                }
                .body<ItineraryDto>()
                .toItinerary()
                .copy(id = id)
        } catch (_: ClientRequestException) {
            null
        }

    override suspend fun departures(
        stopId: String,
        time: Instant?,
        count: Int,
        pageCursor: String?,
    ): DeparturesResult =
        stopTimes {
            parameter("stopId", stopId)
            parameter("withAlerts", true)
            time?.let { parameter("time", it.asParameter()) }
            parameter("n", count)
            pageCursor?.let { parameter("pageCursor", it) }
        }

    override suspend fun lines(stopId: String): List<Line> =
        client
            .get("${baseUrl()}/v6/stop") { parameter("stopId", stopId) }
            .body<StopInfoDto>()
            .routes
            .map { it.toLine() }
            .filter { it.isLine() }
            .distinctBy { it.label to it.mode }
            .sortedWith(
                compareBy<Line> {
                    LineOrder.indexOf(it.mode).let { i ->
                        if (i <
                            0
                        ) {
                            LineOrder.size
                        } else {
                            i
                        }
                    }
                }.thenBy(NaturalOrder) { it.label },
            )

    override suspend fun departuresNear(
        location: LatLng,
        radiusMeters: Int,
        count: Int,
    ): DeparturesResult =
        stopTimes {
            parameter("center", location.asParameter())
            parameter("radius", radiusMeters)
            parameter("n", count)
        }

    private fun HttpRequestBuilder.inLanguage() {
        language()?.takeIf { it.isNotBlank() }?.let { parameter("language", it) }
    }

    private suspend fun stopTimes(block: HttpRequestBuilder.() -> Unit): DeparturesResult {
        val response: StopTimesResponseDto =
            client
                .get("${baseUrl()}/v6/stoptimes") {
                    block()
                    inLanguage()
                }.body()
        return DeparturesResult(
            departures = response.stopTimes.mapNotNull { it.toDeparture() },
            laterCursor = response.nextPageCursor,
        )
    }
}

internal fun LatLng.asParameter(): String = "$latitude,$longitude"

private fun PlanTarget.asParameter(): String =
    when (this) {
        is PlanTarget.Coordinates -> location.asParameter()
        is PlanTarget.Stop -> id
    }

private fun HttpRequestBuilder.routeOptions(
    options: RouteOptions,
    withTransit: Boolean,
) {
    if (!withTransit) {
        parameter("transitModes", "")
    } else if (options.modes.size < ModeGroup.entries.size) {
        val modes = options.modes.flatMap { it.modes }.map(::motisMode) + MINOR_MODES
        parameter("transitModes", modes.joinToString(","))
    }
    options.maxTransfers?.let { parameter("maxTransfers", it) }
    when (options.walkingSpeed) {
        WalkingSpeed.SLOW -> parameter("pedestrianSpeed", SLOW_WALK_METERS_PER_SECOND)
        WalkingSpeed.FAST -> parameter("pedestrianSpeed", FAST_WALK_METERS_PER_SECOND)
        WalkingSpeed.NORMAL -> Unit
    }
    if (options.wheelchair) parameter("pedestrianProfile", "WHEELCHAIR")
    if (options.bikeOnBoard && withTransit) parameter("requireBikeTransport", true)
    if (options.avoidReservations && withTransit) parameter("noCompulsoryReservation", true)
    if (options.sharedVehicles && withTransit) {
        val factors = SharedAccessKinds.flatMap(::formFactors).joinToString(",")
        parameter("preTransitModes", "WALK,RENTAL")
        parameter("postTransitModes", "WALK,RENTAL")
        parameter("preTransitRentalFormFactors", factors)
        parameter("postTransitRentalFormFactors", factors)
    }
}

private fun motisMode(mode: TransportMode): String =
    when (mode) {
        TransportMode.LONG_DISTANCE_RAIL -> "LONG_DISTANCE"
        else -> mode.name
    }

/** Rare modes without a group of their own stay allowed when the user restricts the main ones. */
private val MINOR_MODES = listOf("FUNICULAR", "AERIAL_LIFT", "OTHER")
private const val SLOW_WALK_METERS_PER_SECOND = 0.9

/** Other runs offered per ride, kept small as each costs the server a search. */
private const val LEG_ALTERNATIVES = 3

/** Shared vehicles that can take the user to and from public transport. Cars are left out, as most must go back to their station. */
private val SharedAccessKinds = listOf(VehicleKind.BICYCLE, VehicleKind.SCOOTER)

/** Longest direct route on a shared vehicle, kept short because these searches cost the server the most. */
private const val MAX_SHARED_DIRECT_SECONDS = 60 * 60

/** Longest direct route looked for when more than a walk is asked for. */
private const val MAX_DIRECT_SECONDS = 3 * 60 * 60
private const val FAST_WALK_METERS_PER_SECOND = 1.6

/** MOTIS misreads timestamps with more than millisecond precision, so times are sent in whole seconds. */
internal fun Instant.asParameter(): String = Instant.fromEpochSeconds(epochSeconds).toString()

/** Orders labels like a station sign, so `S2` comes before `S11` and plain numbers before names. */
private val NaturalOrder: Comparator<String> =
    compareBy<String>({ it.takeWhile { c -> !c.isDigit() } }, { it.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }, { it })

// Feeds list long-distance and night trains as one route per train, often under internal numbers, so only lines are kept.
private fun Line.isLine(): Boolean =
    mode !in setOf(TransportMode.HIGHSPEED_RAIL, TransportMode.LONG_DISTANCE_RAIL, TransportMode.NIGHT_RAIL) &&
        label.isNotBlank() &&
        !TrainNumber.matches(label)

/** A single train such as `EN 40237`, rather than a line. */
private val TrainNumber = Regex(".*\\s\\d{3,}")

private val LineOrder =
    listOf(
        TransportMode.SUBURBAN,
        TransportMode.REGIONAL_RAIL,
        TransportMode.SUBWAY,
        TransportMode.TRAM,
        TransportMode.BUS,
        TransportMode.COACH,
        TransportMode.FERRY,
    )
