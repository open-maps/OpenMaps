package nz.eloque.openmaps.ui.directions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import nz.eloque.openmaps.core.model.DistanceUnit
import nz.eloque.openmaps.core.model.EventTime
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.SharedRental
import nz.eloque.openmaps.core.model.TransitRepository
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.Waypoint
import nz.eloque.openmaps.core.model.movedFrom
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.ui.components.AlertNote
import nz.eloque.openmaps.ui.components.Alternatives
import nz.eloque.openmaps.ui.components.BookButton
import nz.eloque.openmaps.ui.components.LineChip
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.LoadStateContent
import nz.eloque.openmaps.ui.components.RideNotes
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.components.formatDuration
import nz.eloque.openmaps.ui.components.icon
import nz.eloque.openmaps.ui.components.label
import nz.eloque.openmaps.ui.components.loadOnce
import nz.eloque.openmaps.ui.components.platformLabel
import nz.eloque.openmaps.ui.components.ruleText
import nz.eloque.openmaps.ui.components.timeRange
import nz.eloque.openmaps.ui.components.withoutLinePrefix
import nz.eloque.openmaps.ui.navigation.RouteKey
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.bike_leg
import nz.eloque.openmaps.ui.resources.drive_leg
import nz.eloque.openmaps.ui.resources.guide_start
import nz.eloque.openmaps.ui.resources.navigation_simulate
import nz.eloque.openmaps.ui.resources.rental_leg
import nz.eloque.openmaps.ui.resources.rental_pick_up
import nz.eloque.openmaps.ui.resources.rental_return
import nz.eloque.openmaps.ui.resources.route_expired
import nz.eloque.openmaps.ui.resources.stops_count
import nz.eloque.openmaps.ui.resources.towards
import nz.eloque.openmaps.ui.resources.train_number
import nz.eloque.openmaps.ui.resources.walk_leg
import nz.eloque.openmaps.ui.resources.your_location
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun RouteSheet(
    route: RouteKey,
    map: MapController,
    snackbar: SnackbarHostState,
    canSimulate: Boolean = false,
    onGuide: (simulate: Boolean) -> Unit = {},
) {
    val cache = koinInject<ItineraryCache>()
    val transit = koinInject<TransitRepository>()
    var attempt by remember { mutableIntStateOf(0) }
    // Guidance finds the itinerary only in the cache.
    val state by produceState<LoadState<Itinerary?>>(
        cache[route.itineraryId]?.let {
            LoadState.Loaded(it)
        } ?: LoadState.Loading,
        route.itineraryId,
        attempt,
    ) {
        if (value is LoadState.Loaded) return@produceState
        value = LoadState.Loading
        value = loadOnce { transit.itinerary(route.itineraryId)?.also { cache.put(listOf(it)) } }
    }
    val itinerary = (state as? LoadState.Loaded)?.value
    ShowItineraryOnMap(map, itinerary)
    LoadStateContent(state, onRetry = { attempt++ }) { loaded ->
        if (loaded == null) {
            Text(
                stringResource(Res.string.route_expired),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            RouteTimeline(loaded, route.destinationName ?: stringResource(Res.string.your_location), snackbar, canSimulate, onGuide)
        }
    }
}

@Composable
private fun RouteTimeline(
    itinerary: Itinerary,
    destinationName: String,
    snackbar: SnackbarHostState,
    canSimulate: Boolean,
    onGuide: (simulate: Boolean) -> Unit,
) {
    val unit by koinInject<SettingsStore>().distanceUnit.collectAsState()
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    timeRange(itinerary.start, itinerary.end),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(formatDuration(itinerary.duration), style = MaterialTheme.typography.titleMedium)
            }
            if (itinerary.legs.any { it is Leg.Transit }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Button(onClick = { onGuide(false) }) {
                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.guide_start), modifier = Modifier.padding(start = 8.dp))
                    }
                    if (canSimulate) OutlinedButton(onClick = { onGuide(true) }) { Text(stringResource(Res.string.navigation_simulate)) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                ReminderButton(itinerary, destinationName, snackbar)
                WatchButton(itinerary, destinationName, snackbar)
            }
            HorizontalDivider()
        }
        items(itinerary.legs) { leg ->
            when (leg) {
                is Leg.Transit -> TransitLegItem(leg)
                is Leg.Street -> StreetLegItem(leg, unit)
            }
        }
        itinerary.legs.lastOrNull()?.let { last ->
            if (last is Leg.Street) {
                item { StopLine(last.end, last.to.copy(name = destinationName), emphasized = true) }
            }
        }
    }
}

@Composable
private fun StreetLegItem(
    leg: Leg.Street,
    unit: DistanceUnit,
) {
    val duration = formatDuration(leg.end.actual - leg.start.actual)
    val label = listOfNotNull(duration, leg.distanceMeters?.let { formatDistance(it, unit) }).joinToString(" · ")
    val rental = leg.rental
    if (rental != null) {
        RentalLegItem(rental, label)
        return
    }
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(leg.mode.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(
                when (leg.mode) {
                    TransportMode.BIKE -> Res.string.bike_leg
                    TransportMode.CAR -> Res.string.drive_leg
                    else -> Res.string.walk_leg
                },
                label,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun RentalLegItem(
    rental: SharedRental,
    label: String,
) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(rental.kind.icon(rental.electric), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.rental_leg, stringResource(rental.kind.label(rental.electric)), rental.operator))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            rental.fromStation?.let {
                Text(stringResource(Res.string.rental_pick_up, it), style = MaterialTheme.typography.bodySmall)
            }
            rental.toStation?.let {
                Text(stringResource(Res.string.rental_return, it), style = MaterialTheme.typography.bodySmall)
            }
            BookButton(rental.operator, rental.booking, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun TransitLegItem(leg: Leg.Transit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StopLine(leg.start, leg.from, emphasized = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineChip(leg.line)
            Column(Modifier.padding(start = 12.dp)) {
                leg.headsign?.let { Text(stringResource(Res.string.towards, it.withoutLinePrefix(leg.line)), maxLines = 1) }
                leg.tripName?.let {
                    Text(
                        stringResource(Res.string.train_number, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        leg.alerts.forEach { AlertNote(it) }
        RideNotes(leg)
        val stops = leg.intermediateStops.size + 1
        Row(
            Modifier.clickable(enabled = leg.intermediateStops.isNotEmpty()) { expanded = !expanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${pluralStringResource(Res.plurals.stops_count, stops, stops)} · ${formatDuration(leg.end.actual - leg.start.actual)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (leg.intermediateStops.isNotEmpty()) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
        }
        if (expanded) {
            leg.intermediateStops.forEach { stop ->
                (stop.departure ?: stop.arrival)?.let { StopLine(it, stop, emphasized = false) }
            }
        }
        StopLine(leg.end, leg.to, emphasized = true)
        Alternatives(leg, Modifier.padding(bottom = 4.dp))
    }
}

@Composable
internal fun StopLine(
    time: EventTime,
    waypoint: Waypoint,
    emphasized: Boolean,
    dimmed: Boolean = false,
) {
    val delayed = time.isRealTime && time.delay.inWholeMinutes >= 1
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp).alpha(if (dimmed) 0.5f else 1f)) {
        Column(Modifier.width(56.dp)) {
            Text(
                clockTime(time.actual),
                fontWeight = if (emphasized) FontWeight.Bold else null,
                color = if (delayed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (waypoint.isCancelled) TextDecoration.LineThrough else null,
            )
            if (delayed) {
                Text(
                    clockTime(time.scheduled),
                    style = MaterialTheme.typography.labelSmall,
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                waypoint.name,
                fontWeight = if (emphasized) FontWeight.SemiBold else null,
                style = if (emphasized) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            )
            waypoint.ruleText()?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (emphasized) {
                platformLabel(waypoint.platform ?: waypoint.scheduledPlatform, waypoint.movedFrom)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
