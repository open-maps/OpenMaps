package nz.eloque.openmaps.data.transit

import nz.eloque.openmaps.core.model.Alert
import nz.eloque.openmaps.core.model.AlertEffect
import nz.eloque.openmaps.core.model.AlertSeverity
import nz.eloque.openmaps.core.model.Continuation
import nz.eloque.openmaps.core.model.Departure
import nz.eloque.openmaps.core.model.EventTime
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.Line
import nz.eloque.openmaps.core.model.PlaceId
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.VehicleKind
import nz.eloque.openmaps.core.model.Waypoint
import nz.eloque.openmaps.core.model.decodePolyline
import kotlin.time.Instant

private fun rentalMode(kind: VehicleKind): TransportMode =
    when (kind) {
        VehicleKind.BICYCLE, VehicleKind.CARGO_BICYCLE -> TransportMode.BIKE
        VehicleKind.SCOOTER, VehicleKind.MOPED -> TransportMode.SCOOTER
        VehicleKind.CAR -> TransportMode.CAR
        VehicleKind.OTHER -> TransportMode.BIKE
    }

internal fun transportMode(motisMode: String): TransportMode =
    when (motisMode) {
        "WALK" -> TransportMode.WALK
        "BIKE", "RENTAL" -> TransportMode.BIKE
        "CAR", "CAR_PARKING", "CAR_DROPOFF", "ODM", "RIDE_SHARING" -> TransportMode.CAR
        "BUS" -> TransportMode.BUS
        "COACH" -> TransportMode.COACH
        "TRAM" -> TransportMode.TRAM
        "SUBWAY", "METRO" -> TransportMode.SUBWAY
        "SUBURBAN" -> TransportMode.SUBURBAN
        "RAIL", "REGIONAL_RAIL", "REGIONAL_FAST_RAIL" -> TransportMode.REGIONAL_RAIL
        "LONG_DISTANCE" -> TransportMode.LONG_DISTANCE_RAIL
        "HIGHSPEED_RAIL" -> TransportMode.HIGHSPEED_RAIL
        "NIGHT_RAIL" -> TransportMode.NIGHT_RAIL
        "FERRY" -> TransportMode.FERRY
        "AIRPLANE" -> TransportMode.AIRPLANE
        "CABLE_CAR", "AERIAL_LIFT" -> TransportMode.CABLE_CAR
        "FUNICULAR" -> TransportMode.FUNICULAR
        else -> TransportMode.OTHER
    }

internal fun parseColor(hex: String?): Long? {
    val digits = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    return digits.toLongOrNull(16)?.let { 0xFF000000 or it }
}

/** Alert texts combine translations with ` -|`. Only the first one is kept. */
private fun firstTranslation(text: String): String = text.substringBefore(" -|").trim()

internal fun AlertDto.toAlert(): Alert? {
    val header = firstTranslation(headerText).ifBlank { return null }
    val description = descriptionText?.let(::firstTranslation)?.takeIf { it.isNotBlank() && it != header }
    val period = impactPeriod.firstOrNull()
    return Alert(
        header = header,
        description = description,
        effect =
            when (effect) {
                "NO_SERVICE" -> AlertEffect.NO_SERVICE
                "REDUCED_SERVICE" -> AlertEffect.REDUCED_SERVICE
                "SIGNIFICANT_DELAYS" -> AlertEffect.DELAYS
                "DETOUR" -> AlertEffect.DETOUR
                "STOP_MOVED" -> AlertEffect.STOP_MOVED
                "ACCESSIBILITY_ISSUE" -> AlertEffect.ACCESSIBILITY
                "MODIFIED_SERVICE" -> AlertEffect.MODIFIED_SERVICE
                "ADDITIONAL_SERVICE" -> AlertEffect.ADDITIONAL_SERVICE
                else -> AlertEffect.OTHER
            },
        severity =
            when (severityLevel) {
                "INFO" -> AlertSeverity.INFO
                "WARNING" -> AlertSeverity.WARNING
                "SEVERE" -> AlertSeverity.SEVERE
                else -> null
            },
        start = period?.start?.let(::parseInstant),
        end = period?.end?.let(::parseInstant),
        url = url?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
    )
}

private fun parseInstant(text: String): Instant? = runCatching { Instant.parse(text) }.getOrNull()

private fun eventTime(
    scheduled: String?,
    actual: String?,
    realTime: Boolean,
): EventTime? {
    val actualInstant = (actual ?: scheduled)?.let(Instant::parse) ?: return null
    val scheduledInstant = scheduled?.let(Instant::parse) ?: actualInstant
    return EventTime(scheduledInstant, actualInstant, realTime)
}

internal fun PlaceDto.toWaypoint(realTime: Boolean): Waypoint =
    Waypoint(
        name = name,
        location = LatLng(lat, lon),
        stopId = stopId,
        platform = track,
        scheduledPlatform = scheduledTrack,
        arrival = eventTime(scheduledArrival, arrival, realTime),
        departure = eventTime(scheduledDeparture, departure, realTime),
        isCancelled = cancelled,
        timeZone = tz,
        canBoard = pickupType != NOT_ALLOWED,
        canAlight = dropoffType != NOT_ALLOWED,
    )

private const val NOT_ALLOWED = "NOT_ALLOWED"

// The display name carries what passengers see, such as `ICE 102`, where the route's short name is often an internal number.
private fun line(
    mode: String,
    shortName: String?,
    displayName: String?,
    longName: String?,
    color: String?,
    textColor: String?,
    agency: String?,
) = Line(
    shortName = displayName?.takeIf { it.isNotBlank() } ?: shortName?.takeIf { it.isNotBlank() },
    longName = longName?.takeIf { it.isNotBlank() },
    mode = transportMode(mode),
    color = parseColor(color),
    textColor = parseColor(textColor),
    agency = agency,
)

internal fun LegDto.toLeg(): Leg {
    val start = eventTime(scheduledStartTime, startTime, realTime)!!
    val end = eventTime(scheduledEndTime, endTime, realTime)!!
    val geometry = decodePolyline(legGeometry.points, legGeometry.precision.toInt())
    val sharedRental = rental?.takeIf { this.mode == "RENTAL" }?.toSharedRental()
    val mode = sharedRental?.let { rentalMode(it.kind) } ?: transportMode(mode)
    if (!mode.isTransit) {
        return Leg.Street(
            mode,
            from.toWaypoint(realTime),
            to.toWaypoint(realTime),
            start,
            end,
            geometry,
            distance,
            mainStreets(steps),
            sharedRental,
        )
    }
    val line = line(this.mode, routeShortName, displayName, routeLongName, routeColor, routeTextColor, agencyName)
    val transit =
        Leg.Transit(
            from = from.toWaypoint(realTime),
            to = to.toWaypoint(realTime),
            start = start,
            end = end,
            geometry = geometry,
            line = line,
            headsign = headsign,
            tripId = tripId,
            intermediateStops = intermediateStops.map { it.toWaypoint(realTime) },
            alerts = alerts.mapNotNull { it.toAlert() },
            isCancelled = cancelled,
            isWheelchairAccessible =
                when (wheelchairAccessible) {
                    "ACCESSIBLE" -> true
                    "NOT_ACCESSIBLE" -> false
                    else -> null
                },
            tripName = tripName(tripShortName, line.label),
            reservationRequired = reservation == "COMPULSORY",
            ticketUrl = listOfNotNull(ticketUrls?.android, ticketUrls?.web, agencyFareUrl).firstOrNull { it.startsWith("https://") },
        )
    return transit.copy(
        alternatives =
            alternatives
                .mapNotNull { alternative -> alternative.toLegs().filterIsInstance<Leg.Transit>().firstOrNull() }
                .filter { it.tripId != tripId && !it.isSameTrainAs(transit) }
                .sortedBy { it.start.actual },
    )
}

/** The run's own number where the line name does not already show it, without leading zeros. */
internal fun tripName(
    tripShortName: String?,
    label: String,
): String? {
    val name = tripShortName?.trim()?.trimStart('0')?.takeIf { it.isNotEmpty() } ?: return null
    return name.takeIf { it !in label }
}

/** Legs with every stay-seated chain joined into one ride. */
internal fun List<LegDto>.toLegs(): List<Leg> =
    fold(mutableListOf()) { legs, dto ->
        val leg = dto.toLeg()
        val previous = legs.lastOrNull()
        if (dto.interlineWithPreviousLeg && previous is Leg.Transit && leg is Leg.Transit) {
            legs[legs.lastIndex] = previous.continuedBy(leg)
        } else {
            legs += leg
        }
        legs
    }

/** This ride and [next], which the same vehicle carries on as, as one ride. */
internal fun Leg.Transit.continuedBy(next: Leg.Transit): Leg.Transit =
    copy(
        to = next.to,
        end = next.end,
        geometry = geometry + next.geometry,
        intermediateStops =
            intermediateStops + to.copy(departure = next.from.departure, platform = next.from.platform) + next.intermediateStops,
        alerts = (alerts + next.alerts).distinct(),
        isCancelled = isCancelled || next.isCancelled,
        isWheelchairAccessible =
            when {
                isWheelchairAccessible == false || next.isWheelchairAccessible == false -> false
                isWheelchairAccessible == true && next.isWheelchairAccessible == true -> true
                else -> null
            },
        continuations = continuations + Continuation(to.name, next.line, next.headsign) + next.continuations,
        reservationRequired = reservationRequired || next.reservationRequired,
        ticketUrl = ticketUrl ?: next.ticketUrl,
    )

internal fun ItineraryDto.toItinerary(): Itinerary {
    val legs = legs.toLegs()
    return Itinerary(
        id = id.ifBlank { "$startTime/$endTime/${this.legs.joinToString(",") { it.mode + it.from.name }}" },
        start = Instant.parse(startTime),
        end = Instant.parse(endTime),
        transfers = (legs.count { it is Leg.Transit } - 1).coerceAtLeast(0),
        legs = legs,
    )
}

internal fun StopTimeDto.toDeparture(arrival: Boolean = false): Departure? {
    val time =
        if (arrival) {
            eventTime(place.scheduledArrival, place.arrival, realTime)
        } else {
            eventTime(place.scheduledDeparture, place.departure, realTime)
        } ?: return null
    val line = line(mode, routeShortName, displayName, routeLongName, routeColor, routeTextColor, agencyName)
    return Departure(
        stopId = place.stopId,
        stopName = place.name,
        stopLocation = LatLng(place.lat, place.lon),
        line = line,
        headsign = headsign,
        time = time,
        platform = place.track,
        tripId = tripId,
        isCancelled = cancelled || tripCancelled,
        alerts = place.alerts.mapNotNull { it.toAlert() },
        passengersAllowed = pickupDropoffType != NOT_ALLOWED,
        scheduledPlatform = place.scheduledTrack,
        origin = tripFrom?.name,
        tripName = tripName(tripShortName, line.label),
    )
}

internal fun RouteDto.toLine(): Line = line(mode, routeShortName, null, routeLongName, routeColor, routeTextColor, agencyName)

internal fun MatchDto.toSearchResult(): SearchResult {
    val location = LatLng(lat, lon)
    val area = areas.firstOrNull { it.isDefault }?.name?.takeIf { it != name }
    return when (type) {
        "STOP" -> SearchResult.Stop(id, name, location, area, modes.map(::transportMode).toSet())
        "ADDRESS" -> SearchResult.Address(name, location, area, street, houseNumber, zip)
        else ->
            SearchResult.Place(
                id.takeIf {
                    OsmObject.matches(it)
                }?.let { PlaceId("osm:$it") },
                name,
                location,
                area,
                category?.replace(IconSizeSuffix, ""),
            )
    }
}

/** The [count] streets with the longest stretches, in the order the route reaches them. */
internal fun mainStreets(
    steps: List<StepDto>,
    count: Int = MAIN_STREETS,
): List<String> {
    val named = steps.filter { it.streetName.isNotBlank() }
    val longest =
        named
            .groupBy { it.streetName }
            .mapValues { (_, stretch) -> stretch.sumOf { it.distance } }
            .entries
            .sortedByDescending { it.value }
            .take(count)
            .map { it.key }
            .toSet()
    return named.map { it.streetName }.distinct().filter { it in longest }
}

private const val MAIN_STREETS = 2

/** MOTIS gives place types as icon names with a size, such as `museum_16`. */
private val IconSizeSuffix = Regex("_\\d+$")

/** Geocoded places carry their OpenStreetMap object as `way/123`. */
private val OsmObject = Regex("(node|way|relation)/\\d+")

/** Long-distance trains carry their number in the line name, so one listed again with that name is the same train from another feed. */
private fun Leg.Transit.isSameTrainAs(other: Leg.Transit): Boolean =
    line.mode in NumberedTrains && other.line.mode in NumberedTrains && line.label == other.line.label

private val NumberedTrains = setOf(TransportMode.HIGHSPEED_RAIL, TransportMode.LONG_DISTANCE_RAIL, TransportMode.NIGHT_RAIL)
