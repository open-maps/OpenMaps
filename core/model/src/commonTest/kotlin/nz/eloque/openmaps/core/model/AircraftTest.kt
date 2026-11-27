package nz.eloque.openmaps.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AircraftTest {
    private val seen = Instant.parse("2026-11-27T10:00:00Z")

    private val eastbound =
        Aircraft("3c6444", "DLH400", null, null, LatLng(50.0, 8.0), 11_000.0, 250.0, 90.0, 0.0, seen)

    @Test
    fun aircraftKeepCourseForAMinuteAtMost() {
        val later = eastbound.positionAt(seen + 10.seconds)
        assertEquals(2500.0, eastbound.location.distanceTo(later), 1.0)
        assertTrue(later.longitude > eastbound.location.longitude)
        assertEquals(
            eastbound.positionAt(seen + Aircraft.MAX_PROJECTION),
            eastbound.positionAt(seen + 5.minutes),
        )
        assertEquals(eastbound.location, eastbound.copy(groundSpeedMetersPerSecond = null).positionAt(seen + 10.seconds))
    }

    @Test
    fun longRoutesFollowTheGreatCircleWithoutJumpingAcrossTheMap() {
        val frankfurt = LatLng(50.03, 8.57)
        val newYork = LatLng(40.64, -73.78)
        val route = frankfurt.greatCircleTo(newYork)
        assertEquals(frankfurt.latitude, route.first().latitude, 1e-6)
        assertEquals(newYork.longitude, route.last().longitude, 1e-6)
        assertTrue(route.maxOf { it.latitude } > 52.0, "The way over the Atlantic bends north")

        val pacific = LatLng(35.55, 139.78).greatCircleTo(LatLng(37.62, -122.38))
        assertTrue(pacific.zipWithNext().all { (a, b) -> kotlin.math.abs(a.longitude - b.longitude) < 10 })
    }
}
