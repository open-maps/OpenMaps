package nz.eloque.openmaps.data.flights

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import nz.eloque.openmaps.core.model.Aircraft
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.RequestsExhausted
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@Serializable
internal data class AircraftListDto(
    val ac: List<AircraftDto> = emptyList(),
    /** Milliseconds since the epoch. */
    val now: Long,
)

@Serializable
internal data class AircraftDto(
    val hex: String,
    val flight: String? = null,
    @SerialName("r") val registration: String? = null,
    @SerialName("t") val type: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    /** Feet, or the string `ground`. */
    @SerialName("alt_baro") val altitude: JsonElement? = null,
    /** Knots. */
    val gs: Double? = null,
    val track: Double? = null,
    /** Feet per minute. */
    @SerialName("baro_rate") val baroRate: Double? = null,
    @SerialName("geom_rate") val geomRate: Double? = null,
    @SerialName("seen_pos") val seenPosition: Double? = null,
)

/** Aircraft positions from an adsb.lol style server, as its community receivers hear them. */
internal class AdsbLol(
    private val client: HttpClient,
    private val baseUrl: Flow<String?>,
) {
    suspend fun near(
        center: LatLng,
        radiusMeters: Double,
    ): List<Aircraft> {
        val radius = ceil(radiusMeters / METERS_PER_NAUTICAL_MILE).toInt().coerceIn(1, MAX_RADIUS_NM)
        return fetch("v2/point/${center.latitude}/${center.longitude}/$radius")
    }

    suspend fun byAddress(id: String): Aircraft? = fetch("v2/hex/${id.encodeURLPathPart()}").firstOrNull()

    suspend fun byCallsign(callsign: String): List<Aircraft> = fetch("v2/callsign/${callsign.encodeURLPathPart()}")

    private suspend fun fetch(path: String): List<Aircraft> {
        val base = baseUrl.first()?.takeIf { it.isNotBlank() }?.trimEnd('/') ?: return emptyList()
        val list =
            try {
                client.get("$base/$path").body<AircraftListDto>()
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.TooManyRequests) throw RequestsExhausted()
                throw e
            }
        return list.ac.mapNotNull { it.toAircraft(Instant.fromEpochMilliseconds(list.now)) }
    }
}

internal fun AircraftDto.toAircraft(now: Instant): Aircraft? {
    val latitude = lat ?: return null
    val longitude = lon ?: return null
    val seen = seenPosition ?: 0.0
    if (seen > MAX_POSITION_AGE_SECONDS) return null
    val feet = (altitude as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
    return Aircraft(
        id = hex.removePrefix("~").lowercase(),
        callsign = flight?.trim()?.takeIf { it.isNotEmpty() },
        registration = registration?.trim()?.takeIf { it.isNotEmpty() },
        typeCode = type?.trim()?.takeIf { it.isNotEmpty() },
        location = LatLng(latitude, longitude),
        altitudeMeters = feet?.times(METERS_PER_FOOT),
        groundSpeedMetersPerSecond = gs?.times(METERS_PER_SECOND_PER_KNOT),
        trackDegrees = track,
        verticalRateMetersPerSecond = (baroRate ?: geomRate)?.times(METERS_PER_FOOT / 60),
        positionTime = now - (seen * 1000).toLong().milliseconds,
    )
}

// The server rejects larger circles.
private const val MAX_RADIUS_NM = 250
private const val MAX_POSITION_AGE_SECONDS = 60.0
private const val METERS_PER_NAUTICAL_MILE = 1852.0
private const val METERS_PER_FOOT = 0.3048
private const val METERS_PER_SECOND_PER_KNOT = 0.514444
