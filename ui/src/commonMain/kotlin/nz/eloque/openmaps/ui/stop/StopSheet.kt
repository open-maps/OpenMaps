package nz.eloque.openmaps.ui.stop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nz.eloque.openmaps.core.model.Departure
import nz.eloque.openmaps.core.model.Line
import nz.eloque.openmaps.core.model.ModeGroup
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.ui.components.DepartureGroupRow
import nz.eloque.openmaps.ui.components.DirectionsButton
import nz.eloque.openmaps.ui.components.FocusOnMap
import nz.eloque.openmaps.ui.components.LineChip
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.LoadStateContent
import nz.eloque.openmaps.ui.components.SaveButton
import nz.eloque.openmaps.ui.components.TimetableRow
import nz.eloque.openmaps.ui.components.dayTime
import nz.eloque.openmaps.ui.components.groupDepartures
import nz.eloque.openmaps.ui.components.rememberNow
import nz.eloque.openmaps.ui.navigation.StopKey
import nz.eloque.openmaps.ui.navigation.toSearchResult
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.arrivals
import nz.eloque.openmaps.ui.resources.board_by_line
import nz.eloque.openmaps.ui.resources.board_choose_time
import nz.eloque.openmaps.ui.resources.board_from
import nz.eloque.openmaps.ui.resources.board_more
import nz.eloque.openmaps.ui.resources.board_now
import nz.eloque.openmaps.ui.resources.board_timetable
import nz.eloque.openmaps.ui.resources.departures
import nz.eloque.openmaps.ui.resources.earlier
import nz.eloque.openmaps.ui.resources.later
import nz.eloque.openmaps.ui.resources.no_arrivals
import nz.eloque.openmaps.ui.resources.no_departures
import nz.eloque.openmaps.ui.resources.show_less
import nz.eloque.openmaps.ui.settings.label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

enum class BoardLayout { BY_LINE, TIMETABLE }

/** A stop's board of departures or arrivals, as a timetable or grouped by line. */
@Composable
fun StopSheet(
    stop: StopKey,
    map: MapController,
    onDirections: () -> Unit,
    onOpenTrip: (tripId: String, stopName: String) -> Unit,
) {
    val viewModel = koinViewModel<StopViewModel> { parametersOf(stop.id) }
    val board by viewModel.board.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val now by rememberNow()
    var hidden by rememberSaveable { mutableStateOf(emptySet<ModeGroup>()) }
    var chosenLayout by rememberSaveable { mutableStateOf<BoardLayout?>(null) }
    val loaded = (board as? LoadState.Loaded)?.value.orEmpty()
    val arrivals = query.kind == BoardKind.ARRIVALS
    val layout =
        when {
            arrivals -> BoardLayout.TIMETABLE
            chosenLayout != null -> chosenLayout!!
            (stop.modes + loaded.map { it.line.mode }).any { it in RailModes } -> BoardLayout.TIMETABLE
            else -> BoardLayout.BY_LINE
        }
    val available = ModeGroup.entries.filter { group -> loaded.any { it.line.mode in group.modes } }
    val shown = loaded.filter { departure -> hidden.none { departure.line.mode in it.modes } }
    val open = { departure: Departure -> departure.tripId?.let { onOpenTrip(it, departure.stopName) } ?: Unit }
    FocusOnMap(map, stop.location)

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        item {
            Text(stop.name, style = MaterialTheme.typography.headlineSmall)
            if (lines.isNotEmpty()) StopLines(lines, Modifier.padding(top = 6.dp))
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DirectionsButton(onDirections)
                SaveButton(stop.toSearchResult())
            }
            BoardHeader(
                kind = query.kind,
                onKind = viewModel::show,
                layout = if (arrivals) null else layout,
                onLayout = { chosenLayout = it },
            )
            BoardControls(
                from = query.from,
                onChoose = viewModel::startAt,
                onEarlier = viewModel::earlier,
                onLater = viewModel::later,
                available = available,
                hidden = hidden,
                onToggle = { group -> hidden = if (group in hidden) hidden - group else hidden + group },
            )
            HorizontalDivider()
        }
        when (val state = board) {
            is LoadState.Loaded ->
                if (shown.isEmpty()) {
                    item {
                        Text(
                            stringResource(if (arrivals) Res.string.no_arrivals else Res.string.no_departures),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                } else if (layout == BoardLayout.TIMETABLE) {
                    val today = now.date()
                    shown.forEachIndexed { index, departure ->
                        val day = departure.time.scheduled.date()
                        val previous = shown.getOrNull(index - 1)?.time?.scheduled?.date()
                        if (day != previous && (previous != null || day != today)) {
                            item(key = "day-$day") { DayHeading(departure.time.scheduled, now) }
                        }
                        item(key = "${departure.tripId}-${departure.stopId}-${departure.time.scheduled}") {
                            TimetableRow(departure, arrival = arrivals, onOpen = open)
                        }
                    }
                    if (state.value.size >= query.count && query.count < MAX_COUNT) {
                        item {
                            TextButton(onClick = viewModel::more, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(Res.string.board_more))
                            }
                        }
                    }
                } else {
                    items(groupDepartures(shown.sortedBy { it.time.actual })) { group ->
                        DepartureGroupRow(group, now, onOpenTrip = { open(group.departures.first()) })
                    }
                }
            else -> item { LoadStateContent(state, onRetry = viewModel::retry) {} }
        }
    }
}

private val RailModes =
    ModeGroup.LONG_DISTANCE_TRAIN.modes + ModeGroup.REGIONAL_TRAIN.modes

private fun Instant.date(): LocalDate = toLocalDateTime(TimeZone.currentSystemDefault()).date

@Composable
private fun DayHeading(
    time: Instant,
    now: Instant,
) {
    Text(
        dayName(time.date(), now.date()),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun BoardHeader(
    kind: BoardKind,
    onKind: (BoardKind) -> Unit,
    layout: BoardLayout?,
    onLayout: (BoardLayout) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        SingleChoiceSegmentedButtonRow {
            BoardKind.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = kind == entry,
                    onClick = { onKind(entry) },
                    shape = SegmentedButtonDefaults.itemShape(index, BoardKind.entries.size),
                ) {
                    Text(stringResource(if (entry == BoardKind.DEPARTURES) Res.string.departures else Res.string.arrivals))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (layout != null) {
            val next = if (layout == BoardLayout.TIMETABLE) BoardLayout.BY_LINE else BoardLayout.TIMETABLE
            IconButton(onClick = { onLayout(next) }) {
                Icon(
                    if (next == BoardLayout.TIMETABLE) Icons.AutoMirrored.Filled.ViewList else Icons.Default.ViewAgenda,
                    contentDescription =
                        stringResource(
                            if (next ==
                                BoardLayout.TIMETABLE
                            ) {
                                Res.string.board_timetable
                            } else {
                                Res.string.board_by_line
                            },
                        ),
                )
            }
        }
    }
}

/** Chips for the board's start time and the kinds of transport shown. */
@Composable
private fun BoardControls(
    from: Instant?,
    onChoose: (Instant?) -> Unit,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    available: List<ModeGroup>,
    hidden: Set<ModeGroup>,
    onToggle: (ModeGroup) -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (from != null) {
            AssistChip(onClick = { onChoose(null) }, label = { Text(stringResource(Res.string.board_now)) })
        }
        AssistChip(
            onClick = { choosing = true },
            label = {
                Text(from?.let { stringResource(Res.string.board_from, dayTime(it)) } ?: stringResource(Res.string.board_choose_time))
            },
            leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        AssistChip(onClick = onEarlier, label = { Text(stringResource(Res.string.earlier)) })
        AssistChip(onClick = onLater, label = { Text(stringResource(Res.string.later)) })
        if (available.size > 1) {
            available.forEach { group ->
                FilterChip(
                    selected = group !in hidden,
                    onClick = { onToggle(group) },
                    label = { Text(stringResource(group.label)) },
                )
            }
        }
    }
    if (choosing) {
        BoardTimeDialog(
            initial = from,
            onDismiss = { choosing = false },
            onPick = {
                choosing = false
                onChoose(it)
            },
        )
    }
}

/** The lines serving the stop, the first few at once and the rest after a tap. */
@Composable
private fun StopLines(
    lines: List<Line>,
    modifier: Modifier = Modifier,
) {
    var all by rememberSaveable { mutableStateOf(false) }
    val shown = if (all) lines else lines.take(SHOWN_LINES)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        shown.forEach { LineChip(it, minWidth = 0.dp) }
        if (lines.size > SHOWN_LINES) {
            Text(
                if (all) stringResource(Res.string.show_less) else "+${lines.size - SHOWN_LINES}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { all = !all }.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

private const val SHOWN_LINES = 12
