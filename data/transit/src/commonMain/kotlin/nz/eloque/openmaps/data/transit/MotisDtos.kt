package nz.eloque.openmaps.data.transit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MOTIS writes some integers in exponent form (`2E0`), so every number is read as Double.

@Serializable
internal data class MatchDto(
    val type: String,
    val name: String,
    val id: String = "",
    val lat: Double,
    val lon: Double,
    val category: String? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    val zip: String? = null,
    val areas: List<AreaDto> = emptyList(),
    val modes: List<String> = emptyList(),
)

@Serializable
internal data class AreaDto(
    val name: String,
    val adminLevel: Double = 0.0,
    @SerialName("default") val isDefault: Boolean = false,
)

@Serializable
internal data class PlaceDto(
    val name: String,
    val lat: Double,
    val lon: Double,
    val stopId: String? = null,
    val tz: String? = null,
    val arrival: String? = null,
    val departure: String? = null,
    val scheduledArrival: String? = null,
    val scheduledDeparture: String? = null,
    val track: String? = null,
    val cancelled: Boolean = false,
    val pickupType: String? = null,
    val dropoffType: String? = null,
    val alerts: List<AlertDto> = emptyList(),
    val modes: List<String> = emptyList(),
)

@Serializable
internal data class AlertDto(
    val headerText: String = "",
    val descriptionText: String? = null,
    val effect: String? = null,
    val severityLevel: String? = null,
    val impactPeriod: List<TimeRangeDto> = emptyList(),
    val url: String? = null,
)

@Serializable
internal data class TimeRangeDto(
    val start: String? = null,
    val end: String? = null,
)

@Serializable
internal data class TicketUrlsDto(
    val web: String? = null,
    val android: String? = null,
)

@Serializable
internal data class RouteDto(
    val mode: String,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val routeColor: String? = null,
    val routeTextColor: String? = null,
    val agencyName: String? = null,
)

@Serializable
internal data class StopInfoDto(
    val routes: List<RouteDto> = emptyList(),
)

@Serializable
internal data class PolylineDto(
    val points: String,
    val precision: Double = 6.0,
)

@Serializable
internal data class LegDto(
    val mode: String,
    val from: PlaceDto,
    val to: PlaceDto,
    val startTime: String,
    val endTime: String,
    val scheduledStartTime: String,
    val scheduledEndTime: String,
    val realTime: Boolean = false,
    val legGeometry: PolylineDto,
    val distance: Double? = null,
    val headsign: String? = null,
    val displayName: String? = null,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val routeColor: String? = null,
    val routeTextColor: String? = null,
    val agencyName: String? = null,
    val agencyFareUrl: String? = null,
    val tripId: String? = null,
    val tripShortName: String? = null,
    val intermediateStops: List<PlaceDto> = emptyList(),
    val alerts: List<AlertDto> = emptyList(),
    val cancelled: Boolean = false,
    val wheelchairAccessible: String? = null,
    val steps: List<StepDto> = emptyList(),
    val rental: RentalDto? = null,
    val interlineWithPreviousLeg: Boolean = false,
    val reservation: String? = null,
    val ticketUrls: TicketUrlsDto? = null,
    val alternatives: List<List<LegDto>> = emptyList(),
)

@Serializable
internal data class RentalDto(
    val systemName: String? = null,
    val providerGroupId: String? = null,
    val fromStationName: String? = null,
    val toStationName: String? = null,
    val rentalUriAndroid: String? = null,
    val rentalUriWeb: String? = null,
    val formFactor: String? = null,
    val propulsionType: String? = null,
)

@Serializable
internal data class StepDto(
    val streetName: String = "",
    val distance: Double = 0.0,
)

@Serializable
internal data class ItineraryDto(
    val id: String = "",
    val startTime: String,
    val endTime: String,
    val transfers: Double = 0.0,
    val legs: List<LegDto>,
)

@Serializable
internal data class PlanResponseDto(
    val itineraries: List<ItineraryDto> = emptyList(),
    val direct: List<ItineraryDto> = emptyList(),
    val previousPageCursor: String? = null,
    val nextPageCursor: String? = null,
)

@Serializable
internal data class StopTimeDto(
    val place: PlaceDto,
    val mode: String,
    val realTime: Boolean = false,
    val headsign: String? = null,
    val displayName: String? = null,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val routeColor: String? = null,
    val routeTextColor: String? = null,
    val agencyName: String? = null,
    val tripId: String? = null,
    val cancelled: Boolean = false,
    val tripCancelled: Boolean = false,
    val pickupDropoffType: String? = null,
)

@Serializable
internal data class StopTimesResponseDto(
    val stopTimes: List<StopTimeDto> = emptyList(),
    val nextPageCursor: String? = null,
)
