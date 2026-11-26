package nz.eloque.openmaps.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class PlatformChangeTest {
    private val at = Instant.parse("2026-11-26T10:00:00Z")

    private fun departure(
        platform: String?,
        scheduled: String?,
        live: Boolean,
    ) = Departure(
        stopId = "s",
        stopName = "Stop",
        stopLocation = LatLng(0.0, 0.0),
        line = Line("RE1", null, TransportMode.REGIONAL_RAIL, null, null, null),
        headsign = null,
        time = EventTime(at, at, live),
        platform = platform,
        tripId = "t",
        isCancelled = false,
        alerts = emptyList(),
        scheduledPlatform = scheduled,
    )

    @Test
    fun onlyLiveDataMovesARunToAnotherPlatform() {
        assertEquals("5", departure("8", "5", live = true).movedFrom)
        assertNull(departure("3", "23", live = false).movedFrom)
        assertNull(departure("8", "8", live = true).movedFrom)
        assertNull(departure("8", null, live = true).movedFrom)
    }
}
