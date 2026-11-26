package nz.eloque.openmaps.core.model

import kotlin.time.Instant

interface Geocoder {
    suspend fun search(
        query: String,
        near: LatLng?,
        stopsFirst: Boolean = false,
    ): List<SearchResult>

    /** The address nearest to [location]. */
    suspend fun reverse(location: LatLng): SearchResult?
}

interface PlaceDetailsSource {
    /** Null for places the source does not know, including ones another source issued [id] for. */
    suspend fun details(id: PlaceId): PlaceDetails?

    /** Details of many places in as few requests as the service allows. Places it does not know are left out. */
    suspend fun detailsOf(ids: List<PlaceId>): Map<PlaceId, PlaceDetails>

    /** A web page about the place, for sharing, or null. */
    fun pageUrl(id: PlaceId): String?
}

sealed interface PlanTarget {
    data class Coordinates(
        val location: LatLng,
    ) : PlanTarget

    data class Stop(
        val id: String,
    ) : PlanTarget
}

data class PlanRequest(
    val from: PlanTarget,
    val to: PlanTarget,
    val time: Instant?,
    val arriveBy: Boolean = false,
    val pageCursor: String? = null,
    val options: RouteOptions = RouteOptions(),
    /** Modes of routes that use no public transport. */
    val directModes: Set<TransportMode> = setOf(TransportMode.WALK),
    /** False to ask for direct routes only, skipping the public transport search. */
    val withTransit: Boolean = true,
    /** Kinds of shared vehicles to offer as routes on their own. */
    val directSharing: Set<VehicleKind> = emptySet(),
)

data class PlanResult(
    val itineraries: List<Itinerary>,
    val direct: List<Itinerary>,
    val earlierCursor: String?,
    val laterCursor: String?,
)

data class DeparturesResult(
    val departures: List<Departure>,
    val laterCursor: String?,
)

interface TransitRepository {
    suspend fun stopsIn(bounds: GeoBounds): List<SearchResult.Stop>

    suspend fun plan(request: PlanRequest): PlanResult

    /** The whole run of one vehicle, as an itinerary with a single transit leg. */
    suspend fun trip(tripId: String): Itinerary

    /** Itinerary [id] from an earlier [plan] with live times, or null when the service cannot rebuild it. */
    suspend fun itinerary(id: String): Itinerary?

    suspend fun departures(
        stopId: String,
        time: Instant? = null,
        count: Int = 30,
        pageCursor: String? = null,
    ): DeparturesResult

    /** The lines that serve stop [stopId], whatever the time. */
    suspend fun lines(stopId: String): List<Line>

    suspend fun departuresNear(
        location: LatLng,
        radiusMeters: Int,
        count: Int = 30,
    ): DeparturesResult
}

/** Problems with the map, reported publicly where mappers fix them. */
interface MapNotes {
    /** Returns a link to the new note. */
    suspend fun report(
        location: LatLng,
        text: String,
    ): String
}
