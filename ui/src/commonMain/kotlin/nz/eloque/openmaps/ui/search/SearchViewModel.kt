package nz.eloque.openmaps.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.AirTraffic
import nz.eloque.openmaps.core.model.Aircraft
import nz.eloque.openmaps.core.model.Favourite
import nz.eloque.openmaps.core.model.FlightInfo
import nz.eloque.openmaps.core.model.Geocoder
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlacesStore
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.formatted
import nz.eloque.openmaps.core.model.parseCoordinates
import nz.eloque.openmaps.core.model.plusCode
import nz.eloque.openmaps.core.model.storageKey
import nz.eloque.openmaps.core.platform.Contacts
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.loadOnce

data class Suggestions(
    val favourites: List<Favourite>,
    val recents: List<SearchResult>,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val geocoder: Geocoder,
    private val places: PlacesStore,
    private val contacts: Contacts,
    private val traffic: AirTraffic,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    var near: LatLng? = null
    var stopsFirst: Boolean = false

    /** Whether queries that look like flight numbers also look for aircraft in the air. */
    var findsFlights: Boolean = false

    /** Shown before typing. Recents that are also favourites are left out. */
    val suggestions: StateFlow<Suggestions> =
        combine(places.favourites, places.recents) { favourites, recents ->
            val saved = favourites.map { it.place.storageKey }.toSet()
            Suggestions(favourites, recents.filterNot { it.storageKey in saved })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Suggestions(emptyList(), emptyList()))

    /** Null while the query is too short to search. */
    val results: StateFlow<LoadState<List<SearchResult>>?> =
        _query
            .debounce(DEBOUNCE_MILLIS)
            .flatMapLatest { query ->
                flow {
                    val trimmed = query.trim()
                    if (trimmed.length < MIN_QUERY_LENGTH) {
                        emit(null)
                    } else {
                        val point = parseCoordinates(trimmed, near)
                        if (point != null) {
                            emit(LoadState.Loaded(listOf(point.toSearchResult())))
                            return@flow
                        }
                        emit(LoadState.Loading)
                        emit(loadOnce { geocoder.search(trimmed, near, stopsFirst) })
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Aircraft flying as the flight number typed, empty for other queries. */
    val flights: StateFlow<List<FlightResult>> =
        _query
            .debounce(DEBOUNCE_MILLIS)
            .flatMapLatest { query ->
                flow {
                    emit(emptyList())
                    val trimmed = query.trim()
                    if (!findsFlights || !FlightLike.matches(trimmed)) return@flow
                    val found =
                        try {
                            traffic.flight(trimmed).take(MAX_FLIGHTS).map { FlightResult(it, traffic.info(it)) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            emptyList()
                        }
                    emit(found)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Searches for an address the user picks from their contacts. */
    fun searchContact() {
        viewModelScope.launch { contacts.pickAddress()?.let(::setQuery) }
    }

    fun remember(result: SearchResult) {
        viewModelScope.launch { places.addRecent(result) }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 300L
        const val MIN_QUERY_LENGTH = 2
        const val MAX_FLIGHTS = 3
    }
}

/** Marks a search result that is only a typed point, which opens like a dropped pin. */
const val COORDINATES_CATEGORY = "coordinates"

private fun LatLng.toSearchResult() =
    SearchResult.Place(id = null, name = formatted(), location = this, area = plusCode(), category = COORDINATES_CATEGORY)

data class FlightResult(
    val aircraft: Aircraft,
    val info: FlightInfo,
)

private val FlightLike = Regex("[A-Za-z0-9]{2,3} ?[0-9]{1,4}[A-Za-z]{0,2}")
