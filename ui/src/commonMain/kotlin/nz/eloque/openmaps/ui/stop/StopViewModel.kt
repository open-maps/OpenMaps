package nz.eloque.openmaps.ui.stop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import nz.eloque.openmaps.core.model.Line
import nz.eloque.openmaps.core.model.TransitRepository
import nz.eloque.openmaps.ui.components.DepartureGroup
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.groupDepartures
import nz.eloque.openmaps.ui.components.refreshing
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StopViewModel(
    private val transit: TransitRepository,
    private val stopId: String,
) : ViewModel() {
    private val retries = MutableStateFlow(0)

    /** When the board starts, or null for now. */
    private val _from = MutableStateFlow<Instant?>(null)
    val from: StateFlow<Instant?> = _from.asStateFlow()

    val departures: StateFlow<LoadState<List<DepartureGroup>>> =
        combine(retries, _from) { _, from -> from }
            .flatMapLatest { from ->
                refreshing { groupDepartures(transit.departures(stopId, time = from, count = 60).departures) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)

    /** Every line serving the stop, empty while loading or when the service fails. */
    val lines: StateFlow<List<Line>> =
        flow {
            emit(
                try {
                    transit.lines(stopId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry() {
        retries.value++
    }

    fun later() {
        _from.value = (_from.value ?: Clock.System.now()) + 1.hours
    }

    fun backToNow() {
        _from.value = null
    }
}
