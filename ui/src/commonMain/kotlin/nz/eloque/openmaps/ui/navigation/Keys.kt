package nz.eloque.openmaps.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.FavouriteLabel
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.PlaceId
import nz.eloque.openmaps.core.model.RouteEnd
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.TravelMode

@Serializable
data object HomeKey : NavKey

/** A screen shown full size over the map, rather than in the sheet. */
sealed interface PageKey : NavKey

@Serializable
data object AboutKey : PageKey

@Serializable
data object DataSourcesKey : PageKey

@Serializable
data object PrivacyKey : PageKey

@Serializable
data object SettingsKey : PageKey

@Serializable
data object OfflineMapsKey : PageKey

/** Choosing a map area on screen to download. */
@Serializable
data object DownloadAreaKey : NavKey

/** Places of one category in the visible map area. */
@Serializable
data class CategoryKey(
    val category: PlaceCategory,
) : NavKey

enum class DirectionsField { FROM, TO, STOP }

/** Search for the main map, for one end of the directions when [field] is set, or to save a favourite when [saveAs] is set. */
@Serializable
data class SearchKey(
    val field: DirectionsField? = null,
    val saveAs: FavouriteLabel? = null,
    val query: String? = null,
) : NavKey

/** A route end. Stops route by id, everything else by coordinates. */
@Serializable
data class Endpoint(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val stopId: String? = null,
) {
    val location: LatLng get() = LatLng(latitude, longitude)
}

/** A null end means the user's location. Routes by car, bike and on foot go through the stops in [via]. */
@Serializable
data class DirectionsKey(
    val from: Endpoint?,
    val to: Endpoint?,
    val via: List<Endpoint> = emptyList(),
) : NavKey

@Serializable
data class RouteKey(
    val itineraryId: String,
    val destinationName: String?,
) : NavKey

fun SearchResult.toEndpoint(): Endpoint = Endpoint(name, location.latitude, location.longitude, (this as? SearchResult.Stop)?.id)

fun PlaceKey.toEndpoint(): Endpoint = Endpoint(name, latitude, longitude)

fun StopKey.toEndpoint(): Endpoint = Endpoint(name, latitude, longitude, id)

@Serializable
data class PlaceKey(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val subtitle: String?,
    val category: String?,
    val placeId: String?,
    /** True for a pin dropped by long-pressing the map. */
    val pinned: Boolean = false,
) : NavKey {
    val location: LatLng get() = LatLng(latitude, longitude)
}

@Serializable
data class StopKey(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val modes: Set<TransportMode> = emptySet(),
) : NavKey {
    val location: LatLng get() = LatLng(latitude, longitude)
}

fun StopKey.toSearchResult(): SearchResult = SearchResult.Stop(id, name, location, null, modes)

fun PlaceKey.toSearchResult(): SearchResult =
    if (placeId == null && category == null) {
        SearchResult.Address(name, location, subtitle, null, null, null)
    } else {
        SearchResult.Place(parsedId, name, location, subtitle, category)
    }

fun SearchResult.toKey(): NavKey =
    when (this) {
        is SearchResult.Stop -> StopKey(id, name, location.latitude, location.longitude, modes)
        is SearchResult.Place ->
            PlaceKey(name, location.latitude, location.longitude, area, category, id?.value)
        is SearchResult.Address -> {
            val subtitle = listOfNotNull(postcode, area).joinToString(" ").takeIf { it.isNotBlank() }
            PlaceKey(name, location.latitude, location.longitude, subtitle, category = null, placeId = null)
        }
    }

val PlaceKey.parsedId: PlaceId? get() = placeId?.let(::PlaceId)

/** One vehicle's run. [stopName] is the stop it was opened from, which is highlighted. */
@Serializable
data class TripKey(
    val tripId: String,
    val stopName: String?,
) : NavKey

@Serializable
data object SavedKey : NavKey

/** The results of a typed search shown together on the map. */
@Serializable
data class SearchResultsKey(
    val query: String,
) : NavKey

@Serializable
data object TripsKey : NavKey

/** A saved track by its [nz.eloque.openmaps.core.model.TrackStore] id. */
@Serializable
data class TrackKey(
    val id: Long,
) : NavKey

/** Measuring a distance on the map, starting at a point. */
@Serializable
data class MeasureKey(
    val latitude: Double,
    val longitude: Double,
) : NavKey

/** A trip with turn-by-turn guidance. A null [from] starts at the user's location. [simulate] drives the route by itself. */
@Serializable
data class NavigationKey(
    val from: Endpoint?,
    val to: Endpoint,
    val mode: TravelMode,
    val simulate: Boolean = false,
    val via: List<Endpoint> = emptyList(),
    /** A planned route the user chose over the fastest one, kept in [nz.eloque.openmaps.ui.directions.ItineraryCache]. */
    val routeId: String? = null,
) : NavKey

/** Guidance along a cached public transport itinerary. [simulate] follows it on the timetable by itself. */
@Serializable
data class TransitGuidanceKey(
    val itineraryId: String,
    val destinationName: String?,
    val simulate: Boolean = false,
) : NavKey

/** A tapped road event, as the properties of its map feature. */
@Serializable
data class RoadEventKey(
    val properties: Map<String, String>,
) : NavKey

fun Endpoint.toRouteEnd(): RouteEnd = RouteEnd(name, location, stopId)

fun RouteEnd.toEndpoint(): Endpoint = Endpoint(name, location.latitude, location.longitude, stopId)

/** One of the user's lists of places. */
@Serializable
data class ListKey(
    val listId: Long,
) : NavKey

/** One aircraft by its transponder address. [label] names it until its data arrives. */
@Serializable
data class AircraftKey(
    val id: String,
    val label: String?,
) : NavKey
