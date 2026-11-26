package nz.eloque.openmaps.ui.directions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.ui.components.AlertNote
import nz.eloque.openmaps.ui.components.LineChip
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.LoadStateContent
import nz.eloque.openmaps.ui.components.RideNotes
import nz.eloque.openmaps.ui.components.rememberNow
import nz.eloque.openmaps.ui.components.withoutLinePrefix
import nz.eloque.openmaps.ui.navigation.TripKey
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.towards
import nz.eloque.openmaps.ui.resources.train_number
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** One vehicle's run with every stop. Stops already passed are dimmed and the stop it was opened from is emphasized. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSheet(
    trip: TripKey,
    map: MapController,
) {
    val viewModel = koinViewModel<TripViewModel> { parametersOf(trip.tripId) }
    val state by viewModel.trip.collectAsStateWithLifecycle()
    val now by rememberNow()
    val itinerary = (state as? LoadState.Loaded)?.value
    ShowItineraryOnMap(map, itinerary)

    val leg = itinerary?.legs?.filterIsInstance<Leg.Transit>()?.firstOrNull()
    if (leg == null) {
        LoadStateContent(if (itinerary != null) LoadState.Failed else state, onRetry = viewModel::retry) {}
        return
    }
    val stops = listOf(leg.from) + leg.intermediateStops + leg.to
    val list = rememberLazyListState()
    val openedAt = stops.indexOfFirst { it.name == trip.stopName }
    LaunchedEffect(trip.tripId, openedAt >= 0) {
        if (openedAt > 1) list.scrollToItem(HEADER_ITEMS + leg.alerts.size + openedAt - 2)
    }
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), state = list) {
        stickyHeader {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(BottomSheetDefaults.ContainerColor).padding(bottom = 8.dp),
            ) {
                LineChip(leg.line)
                Column(Modifier.padding(start = 12.dp)) {
                    leg.headsign?.let {
                        Text(
                            stringResource(Res.string.towards, it.withoutLinePrefix(leg.line)),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    leg.tripName?.let {
                        Text(
                            stringResource(Res.string.train_number, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            RideNotes(leg.copy(ticketUrl = null), Modifier.padding(bottom = 6.dp))
        }
        items(leg.alerts) { alert -> AlertNote(alert, Modifier.padding(bottom = 6.dp)) }
        item { HorizontalDivider(Modifier.padding(bottom = 8.dp)) }
        items(stops) { stop ->
            val time = stop.departure ?: stop.arrival ?: return@items
            StopLine(time, stop, emphasized = stop.name == trip.stopName, dimmed = time.actual < now)
        }
    }
}

/** The line, its notes and the divider above the stops. */
private const val HEADER_ITEMS = 3
