package nz.eloque.openmaps.core.model

import kotlin.time.Duration
import kotlin.time.Instant

data class EventTime(
    val scheduled: Instant,
    val actual: Instant,
    val isRealTime: Boolean,
) {
    val delay: Duration
        get() = actual - scheduled
}

data class Waypoint(
    val name: String,
    val location: LatLng,
    val stopId: String?,
    val platform: String?,
    val arrival: EventTime?,
    val departure: EventTime?,
    val isCancelled: Boolean,
    val timeZone: String?,
    /** False where the vehicle stops but passengers may not get on. */
    val canBoard: Boolean = true,
    /** False where the vehicle stops but passengers may not get off. */
    val canAlight: Boolean = true,
)

data class Line(
    val shortName: String?,
    val longName: String?,
    val mode: TransportMode,
    /** ARGB, or null when the feed has no colour. */
    val color: Long?,
    val textColor: Long?,
    val agency: String?,
) {
    val label: String
        get() = shortName ?: longName ?: ""
}

/** What a disruption does to the service. */
enum class AlertEffect {
    NO_SERVICE,
    REDUCED_SERVICE,
    DELAYS,
    DETOUR,
    STOP_MOVED,
    ACCESSIBILITY,
    MODIFIED_SERVICE,
    ADDITIONAL_SERVICE,
    OTHER,
}

enum class AlertSeverity { INFO, WARNING, SEVERE }

data class Alert(
    val header: String,
    val description: String?,
    val effect: AlertEffect = AlertEffect.OTHER,
    /** Null where the feed does not say. */
    val severity: AlertSeverity? = null,
    /** When the disruption applies, null where open or unknown. */
    val start: Instant? = null,
    val end: Instant? = null,
    val url: String? = null,
)

sealed interface Leg {
    val from: Waypoint
    val to: Waypoint
    val start: EventTime
    val end: EventTime
    val geometry: List<LatLng>
    val mode: TransportMode

    data class Street(
        override val mode: TransportMode,
        override val from: Waypoint,
        override val to: Waypoint,
        override val start: EventTime,
        override val end: EventTime,
        override val geometry: List<LatLng>,
        val distanceMeters: Double?,
        /** Names of the streets covering most of the leg, in travel order. */
        val streets: List<String> = emptyList(),
        /** The shared vehicle ridden on this leg, or null for the user's own feet, bike or car. */
        val rental: SharedRental? = null,
        /** Null when the routing service gave no heights. */
        val elevation: ElevationProfile? = null,
        /** Kinds of road this leg uses that drivers may want to know about beforehand. */
        val features: Set<RouteFeature> = emptySet(),
    ) : Leg

    data class Transit(
        override val from: Waypoint,
        override val to: Waypoint,
        override val start: EventTime,
        override val end: EventTime,
        override val geometry: List<LatLng>,
        val line: Line,
        val headsign: String?,
        val tripId: String?,
        val intermediateStops: List<Waypoint>,
        val alerts: List<Alert>,
        val isCancelled: Boolean,
        val isWheelchairAccessible: Boolean?,
        /** The operator's number for this run, such as `4739` for a regional train, where the line name lacks it. */
        val tripName: String? = null,
        /** Where the vehicle carries on as another line while the user stays on board, in travel order. */
        val continuations: List<Continuation> = emptyList(),
        val reservationRequired: Boolean = false,
        /** Where to buy a ticket for this ride, null where the operator gives no link. */
        val ticketUrl: String? = null,
        /** Other runs that fit the same gap in the journey, earliest first. */
        val alternatives: List<Transit> = emptyList(),
    ) : Leg {
        override val mode: TransportMode
            get() = line.mode
    }
}

/** A vehicle carrying on as [line] from stop [at], without passengers having to change. */
data class Continuation(
    val at: String,
    val line: Line,
    val headsign: String?,
)

enum class RouteFeature {
    TOLLS,
    MOTORWAYS,
    FERRIES,
}

data class Itinerary(
    val id: String,
    val start: Instant,
    val end: Instant,
    val transfers: Int,
    val legs: List<Leg>,
) {
    val duration: Duration
        get() = end - start
}

/** The route to suggest first: the earliest arrival, or with [arriveBy] the latest departure, then fewer transfers, then the shorter ride. */
fun List<Itinerary>.best(arriveBy: Boolean = false): Itinerary? =
    minWithOrNull(
        compareBy<Itinerary> { if (arriveBy) -it.start.toEpochMilliseconds() else it.end.toEpochMilliseconds() }
            .thenBy { it.transfers }
            .thenBy { it.duration },
    )

data class Departure(
    val stopId: String?,
    val stopName: String,
    val stopLocation: LatLng,
    val line: Line,
    val headsign: String?,
    val time: EventTime,
    val platform: String?,
    val tripId: String?,
    val isCancelled: Boolean,
    val alerts: List<Alert>,
    /** False for a run that only lets passengers off here. */
    val canBoard: Boolean = true,
)
