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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import nz.eloque.openmaps.core.model.Departure
import nz.eloque.openmaps.core.model.Line
import nz.eloque.openmaps.core.model.TransitRepository
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.refreshing
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

enum class BoardKind { DEPARTURES, ARRIVALS }

data class BoardQuery(
    val kind: BoardKind = BoardKind.DEPARTURES,
    /** When the board starts, or null for now. */
    val from: Instant? = null,
    val count: Int = PAGE,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StopViewModel(
    private val transit: TransitRepository,
    private val stopId: String,
) : ViewModel() {
    private val retries = MutableStateFlow(0)

    private val _query = MutableStateFlow(BoardQuery())
    val query: StateFlow<BoardQuery> = _query.asStateFlow()

    val board: StateFlow<LoadState<List<Departure>>> =
        combine(retries, _query) { _, query -> query }
            .runningFold(null as Pair<BoardQuery?, BoardQuery>?) { last, query -> last?.second to query }
            .filterNotNull()
            .flatMapLatest { (previous, query) ->
                val lengthened = previous != null && previous.copy(count = query.count) == query && query.count > previous.count
                refreshing {
                    transit
                        .departures(stopId, time = query.from, count = query.count, arrivals = query.kind == BoardKind.ARRIVALS)
                        .departures
                        .sortedBy { it.time.scheduled }
                }.filter { !lengthened || it !is LoadState.Loading }
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

    fun show(kind: BoardKind) {
        _query.update { it.copy(kind = kind, count = PAGE) }
    }

    fun startAt(time: Instant?) {
        _query.update { it.copy(from = time, count = PAGE) }
    }

    fun earlier() = startAt((_query.value.from ?: Clock.System.now()) - 1.hours)

    fun later() = startAt((_query.value.from ?: Clock.System.now()) + 1.hours)

    /** Lengthens the board, up to [MAX_COUNT] so refreshes stay light. */
    fun more() {
        _query.update { it.copy(count = (it.count + PAGE).coerceAtMost(MAX_COUNT)) }
    }
}

private const val PAGE = 60
const val MAX_COUNT = 180
