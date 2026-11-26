package nz.eloque.openmaps.ui.directions

import nz.eloque.openmaps.core.model.EventTime
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.Line
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.Waypoint
import nz.eloque.openmaps.ui.components.clockTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class CalendarDescriptionTest {
    private val leave = Instant.parse("2026-11-27T07:23:00Z")
    private val arrive = Instant.parse("2026-11-27T08:17:00Z")

    private fun stop(
        name: String,
        platform: String?,
    ) = Waypoint(name, LatLng(0.0, 0.0), name, platform, null, null, false, null)

    @Test
    fun listsEachRideWithItsStopsTimesAndPlatforms() {
        val ride =
            Leg.Transit(
                stop("Karlsruhe Hbf", "3"),
                stop("Stuttgart Hbf", null),
                EventTime(leave, leave + 4.minutes, true),
                EventTime(arrive, arrive, true),
                emptyList(),
                Line("RE 17", null, TransportMode.REGIONAL_RAIL, null, null, null),
                "Stuttgart Hbf",
                "t",
                emptyList(),
                emptyList(),
                false,
                null,
            )
        val walk =
            Leg.Street(
                TransportMode.WALK,
                stop("Home", null),
                ride.from,
                EventTime(leave, leave, false),
                EventTime(leave, leave, false),
                emptyList(),
                0.0,
            )
        val itinerary = Itinerary("i", leave, arrive, 0, listOf(walk, ride))

        assertEquals(
            "RE 17 → Stuttgart Hbf\n${clockTime(leave)} Karlsruhe Hbf, Pl. 3\n${clockTime(arrive)} Stuttgart Hbf",
            itinerary.calendarDescription { "Pl. $it" },
        )
    }
}
