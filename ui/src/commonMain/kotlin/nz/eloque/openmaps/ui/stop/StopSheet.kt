package nz.eloque.openmaps.ui.stop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.components.rememberNow
import nz.eloque.openmaps.ui.navigation.StopKey
import nz.eloque.openmaps.ui.navigation.toSearchResult
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.board_from
import nz.eloque.openmaps.ui.resources.board_now
import nz.eloque.openmaps.ui.resources.departures
import nz.eloque.openmaps.ui.resources.later
import nz.eloque.openmaps.ui.resources.no_departures
import nz.eloque.openmaps.ui.resources.show_less
import nz.eloque.openmaps.ui.settings.label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

@Composable
fun StopSheet(
    stop: StopKey,
    map: MapController,
    onDirections: () -> Unit,
    onOpenTrip: (tripId: String) -> Unit,
) {
    val viewModel = koinViewModel<StopViewModel> { parametersOf(stop.id) }
    val departures by viewModel.departures.collectAsStateWithLifecycle()
    val now by rememberNow()
    val from by viewModel.from.collectAsStateWithLifecycle()
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    var hidden by rememberSaveable { mutableStateOf(emptySet<ModeGroup>()) }
    val loaded = (departures as? LoadState.Loaded)?.value.orEmpty()
    val available = ModeGroup.entries.filter { group -> loaded.any { it.line.mode in group.modes } }
    FocusOnMap(map, stop.location)

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        item {
            Text(stop.name, style = MaterialTheme.typography.headlineSmall)
            if (lines.isNotEmpty()) StopLines(lines, Modifier.padding(top = 6.dp))
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DirectionsButton(onDirections)
                SaveButton(stop.toSearchResult())
            }
            Text(
                stringResource(Res.string.departures),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            BoardControls(
                from = from,
                onLater = viewModel::later,
                onNow = viewModel::backToNow,
                available = available,
                hidden = hidden,
                onToggle = { group -> hidden = if (group in hidden) hidden - group else hidden + group },
            )
            HorizontalDivider()
        }
        when (val state = departures) {
            is LoadState.Loaded ->
                if (state.value.isEmpty()) {
                    item {
                        Text(
                            stringResource(Res.string.no_departures),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                } else {
                    items(state.value.filter { group -> hidden.none { group.line.mode in it.modes } }) {
                        DepartureGroupRow(it, now, onOpenTrip = onOpenTrip)
                    }
                }
            else -> item { LoadStateContent(state, onRetry = viewModel::retry) {} }
        }
    }
}

/** Chips to hide kinds of transport, shown when the stop has more than one, and to move the board to later departures. */
@Composable
private fun BoardControls(
    from: Instant?,
    onLater: () -> Unit,
    onNow: () -> Unit,
    available: List<ModeGroup>,
    hidden: Set<ModeGroup>,
    onToggle: (ModeGroup) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (from != null) {
            AssistChip(onClick = onNow, label = { Text(stringResource(Res.string.board_now)) })
        }
        AssistChip(
            onClick = onLater,
            label = {
                Text(
                    if (from == null) stringResource(Res.string.later) else stringResource(Res.string.board_from, clockTime(from)),
                )
            },
            leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
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
