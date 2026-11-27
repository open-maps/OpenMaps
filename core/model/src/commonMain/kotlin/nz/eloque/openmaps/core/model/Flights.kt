package nz.eloque.openmaps.core.model

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** An aircraft as its transponder last reported it. */
data class Aircraft(
    /** The 24-bit ICAO address in hex, fixed for the airframe. */
    val id: String,
    val callsign: String?,
    val registration: String?,
    /** ICAO type designator, such as `A320`. */
    val typeCode: String?,
    val location: LatLng,
    /** Null on the ground. */
    val altitudeMeters: Double?,
    val groundSpeedMetersPerSecond: Double?,
    /** Degrees clockwise from true north. */
    val trackDegrees: Double?,
    /** Positive while climbing. */
    val verticalRateMetersPerSecond: Double?,
    /** When [location] was measured. */
    val positionTime: Instant,
) {
    val onGround: Boolean
        get() = altitudeMeters == null

    /** Where the aircraft is at [time] if it kept its course and speed, projected at most [MAX_PROJECTION] past [positionTime]. */
    fun positionAt(time: Instant): LatLng {
        val speed = groundSpeedMetersPerSecond ?: return location
        val track = trackDegrees ?: return location
        val elapsed = (time - positionTime).coerceIn(Duration.ZERO, MAX_PROJECTION)
        return location.moved(speed * elapsed.inWholeMilliseconds / 1000.0, track)
    }

    companion object {
        val MAX_PROJECTION = 60.seconds
    }
}

data class Airport(
    val icao: String,
    val iata: String?,
    val name: String,
    val city: String?,
    val location: LatLng,
)

/** What a flight's data sources know about it beyond its position. Each part is null where unknown. */
data class FlightInfo(
    val airline: String?,
    /** Manufacturer and model, such as `Airbus A320neo`. */
    val model: String?,
    /** Where flights with this callsign usually come from, which can differ on a given day. */
    val origin: Airport?,
    val destination: Airport?,
)

/** The service refused further requests until its allowance renews. */
class RequestsExhausted : Exception()

interface AirTraffic {
    /** Aircraft within [radiusMeters] of [center]. Sources may cap the radius. */
    suspend fun near(
        center: LatLng,
        radiusMeters: Double,
    ): List<Aircraft>

    /** The aircraft with address [id], or null when it is not being received. */
    suspend fun aircraft(id: String): Aircraft?

    suspend fun info(aircraft: Aircraft): FlightInfo
}

/** The point [meters] away from here in direction [bearing], along a great circle. */
fun LatLng.moved(
    meters: Double,
    bearing: Double,
): LatLng {
    if (meters == 0.0) return this
    val angle = meters / EARTH_RADIUS
    val heading = bearing.radians
    val lat = latitude.radians
    val lon = longitude.radians
    val newLat = asin(sin(lat) * cos(angle) + cos(lat) * sin(angle) * cos(heading))
    val newLon = lon + atan2(sin(heading) * sin(angle) * cos(lat), cos(angle) - sin(lat) * sin(newLat))
    return LatLng(newLat.degrees, ((newLon.degrees + 540) % 360) - 180)
}

/** Points along the great circle from here to [other], for drawing long flights as they are flown. */
fun LatLng.greatCircleTo(
    other: LatLng,
    segments: Int = 64,
): List<LatLng> {
    val distance = distanceTo(other)
    if (distance < 1.0) return listOf(this, other)
    val lat1 = latitude.radians
    val lon1 = longitude.radians
    val lat2 = other.latitude.radians
    val lon2 = other.longitude.radians
    val angle = distance / EARTH_RADIUS
    val points =
        (0..segments).map { step ->
            val f = step.toDouble() / segments
            val a = sin((1 - f) * angle) / sin(angle)
            val b = sin(f * angle) / sin(angle)
            val x = a * cos(lat1) * cos(lon1) + b * cos(lat2) * cos(lon2)
            val y = a * cos(lat1) * sin(lon1) + b * cos(lat2) * sin(lon2)
            val z = a * sin(lat1) + b * sin(lat2)
            LatLng(atan2(z, sqrt(x * x + y * y)).degrees, atan2(y, x).degrees)
        }
    return points.unwrapped()
}

// Lines crossing the antimeridian would otherwise be drawn the long way round the world.
private fun List<LatLng>.unwrapped(): List<LatLng> {
    if (isEmpty()) return this
    val result = mutableListOf(first())
    for (point in drop(1)) {
        val previous = result.last().longitude
        var longitude = point.longitude
        while (longitude - previous > 180) longitude -= 360
        while (longitude - previous < -180) longitude += 360
        result += point.copy(longitude = longitude)
    }
    return result
}

private const val EARTH_RADIUS = 6_371_000.0

private val Double.radians get() = this * PI / 180

private val Double.degrees get() = this * 180 / PI
