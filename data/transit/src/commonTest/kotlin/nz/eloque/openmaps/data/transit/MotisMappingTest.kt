package nz.eloque.openmaps.data.transit

import nz.eloque.openmaps.core.model.AlertEffect
import nz.eloque.openmaps.core.model.AlertSeverity
import nz.eloque.openmaps.core.model.Leg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun place(
    name: String,
    time: String,
    pickup: String? = null,
    dropoff: String? = null,
) = PlaceDto(
    name = name,
    lat = 49.0,
    lon = 8.4,
    stopId = name,
    arrival = time,
    departure = time,
    scheduledArrival = time,
    scheduledDeparture = time,
    pickupType = pickup,
    dropoffType = dropoff,
)

private fun transit(
    from: String,
    to: String,
    start: String,
    end: String,
    displayName: String,
    tripId: String,
    interline: Boolean = false,
    intermediate: List<PlaceDto> = emptyList(),
) = LegDto(
    mode = "REGIONAL_RAIL",
    from = place(from, start),
    to = place(to, end),
    startTime = start,
    endTime = end,
    scheduledStartTime = start,
    scheduledEndTime = end,
    legGeometry = PolylineDto(""),
    displayName = displayName,
    routeShortName = "29",
    tripId = tripId,
    intermediateStops = intermediate,
    interlineWithPreviousLeg = interline,
)

class MotisMappingTest {
    @Test
    fun alertsKeepEffectSeverityPeriodAndLink() {
        val alert =
            AlertDto(
                headerText = "Gleisarbeiten -| Track works",
                effect = "NO_SERVICE",
                severityLevel = "SEVERE",
                impactPeriod = listOf(TimeRangeDto("2026-11-26T05:00:00Z", "2026-11-28T22:00:00Z")),
                url = "https://example.org/works",
            ).toAlert()!!
        assertEquals("Gleisarbeiten", alert.header)
        assertEquals(AlertEffect.NO_SERVICE, alert.effect)
        assertEquals(AlertSeverity.SEVERE, alert.severity)
        assertEquals(Instant.parse("2026-11-28T22:00:00Z"), alert.end)
        assertEquals("https://example.org/works", alert.url)
        assertNull(AlertDto(headerText = "x", url = "javascript:alert(1)").toAlert()!!.url)
    }

    @Test
    fun linesShowTheDisplayNameAndRunsTheirNumberOnlyWhereMissing() {
        val ice = transit("Karlsruhe", "Mannheim", "2026-11-26T10:00:00Z", "2026-11-26T10:25:00Z", "ICE 102", "t1").toLeg() as Leg.Transit
        assertEquals("ICE 102", ice.line.label)
        assertNull(tripName("102", ice.line.label))
        assertEquals("4739", tripName("004739", "RE2"))
        assertNull(tripName("", "2"))
    }

    @Test
    fun staySeatedRidesBecomeOneRideNamingTheContinuation() {
        val legs =
            listOf(
                transit("A", "B", "2026-11-26T10:00:00Z", "2026-11-26T10:20:00Z", "RE1", "t1"),
                transit("B", "C", "2026-11-26T10:22:00Z", "2026-11-26T10:40:00Z", "RE3", "t2", interline = true),
            ).toLegs()
        val ride = legs.single() as Leg.Transit
        assertEquals("A", ride.from.name)
        assertEquals("C", ride.to.name)
        assertEquals(listOf("B"), ride.intermediateStops.map { it.name })
        assertEquals(Instant.parse("2026-11-26T10:22:00Z"), ride.intermediateStops.single().departure!!.actual)
        assertEquals("RE3", ride.continuations.single().line.label)
        assertEquals("B", ride.continuations.single().at)
        assertEquals("t1", ride.tripId)

        val itinerary =
            ItineraryDto(
                startTime = "2026-11-26T10:00:00Z",
                endTime = "2026-11-26T10:40:00Z",
                transfers = 1.0,
                legs =
                    listOf(
                        transit("A", "B", "2026-11-26T10:00:00Z", "2026-11-26T10:20:00Z", "RE1", "t1"),
                        transit("B", "C", "2026-11-26T10:22:00Z", "2026-11-26T10:40:00Z", "RE3", "t2", interline = true),
                    ),
            ).toItinerary()
        assertEquals(0, itinerary.transfers)
    }

    @Test
    fun stopsTellWhereBoardingOrAlightingIsNotAllowed() {
        val stop = place("B", "2026-11-26T10:10:00Z", pickup = "NOT_ALLOWED").toWaypoint(realTime = false)
        assertFalse(stop.canBoard)
        assertTrue(stop.canAlight)
    }

    @Test
    fun alternativesAreOtherRunsInOrder() {
        val later = transit("A", "B", "2026-11-26T10:30:00Z", "2026-11-26T10:50:00Z", "RE1", "t3")
        val earlier = transit("A", "B", "2026-11-26T09:30:00Z", "2026-11-26T09:50:00Z", "RE1", "t0")
        val same = transit("A", "B", "2026-11-26T10:00:00Z", "2026-11-26T10:20:00Z", "RE1", "t1")
        val leg =
            transit("A", "B", "2026-11-26T10:00:00Z", "2026-11-26T10:20:00Z", "RE1", "t1")
                .copy(alternatives = listOf(listOf(later), listOf(same), listOf(earlier)))
                .toLeg() as Leg.Transit
        assertEquals(listOf("t0", "t3"), leg.alternatives.map { it.tripId })
    }

    @Test
    fun aLongDistanceTrainFromAnotherFeedIsNoAlternative() {
        val ice = {
            start: String,
            tripId: String,
            ->
            transit("A", "B", start, "2026-11-26T11:00:00Z", "ICE 9579", tripId).copy(mode = "HIGHSPEED_RAIL")
        }
        val leg =
            ice("2026-11-26T10:00:00Z", "t1")
                .copy(alternatives = listOf(listOf(ice("2026-11-26T10:10:00Z", "other-feed"))))
                .toLeg() as Leg.Transit
        assertTrue(leg.alternatives.isEmpty())
    }
}

class StopTimeMappingTest {
    private val stopTime =
        StopTimeDto(
            place =
                PlaceDto(
                    name = "Karlsruhe Hbf",
                    lat = 49.0,
                    lon = 8.4,
                    stopId = "k",
                    arrival = "2026-11-26T10:03:00Z",
                    scheduledArrival = "2026-11-26T10:00:00Z",
                    departure = "2026-11-26T10:06:00Z",
                    scheduledDeparture = "2026-11-26T10:05:00Z",
                    track = "7",
                    scheduledTrack = "4",
                ),
            mode = "REGIONAL_RAIL",
            realTime = true,
            displayName = "RE2",
            tripShortName = "004739",
            tripFrom = PlaceDto(name = "Mannheim Hbf", lat = 49.5, lon = 8.5),
            pickupDropoffType = "NOT_ALLOWED",
        )

    @Test
    fun aDepartureKeepsBothPlatformsAndTheTrainNumber() {
        val departure = stopTime.toDeparture()!!
        assertEquals(Instant.parse("2026-11-26T10:05:00Z"), departure.time.scheduled)
        assertEquals("7", departure.platform)
        assertEquals("4", departure.scheduledPlatform)
        assertEquals("4739", departure.tripName)
        assertFalse(departure.passengersAllowed)
    }

    @Test
    fun anArrivalUsesTheArrivalTimeAndNamesWhereTheRunStarted() {
        val arrival = stopTime.toDeparture(arrival = true)!!
        assertEquals(Instant.parse("2026-11-26T10:00:00Z"), arrival.time.scheduled)
        assertEquals(Instant.parse("2026-11-26T10:03:00Z"), arrival.time.actual)
        assertEquals("Mannheim Hbf", arrival.origin)
    }
}
