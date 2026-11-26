package nz.eloque.openmaps.ui.directions

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.Itinerary
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.Waypoint
import nz.eloque.openmaps.core.platform.CalendarEvent
import nz.eloque.openmaps.core.platform.EventCalendar
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.add_to_calendar
import nz.eloque.openmaps.ui.resources.calendar_title
import nz.eloque.openmaps.ui.resources.no_calendar_app
import nz.eloque.openmaps.ui.resources.platform_short
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Instant

@Composable
fun CalendarButton(
    itinerary: Itinerary,
    destinationName: String,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val calendar = koinInject<EventCalendar>()
    val scope = rememberCoroutineScope()
    OutlinedButton(
        onClick = {
            scope.launch {
                val platforms =
                    itinerary.legs
                        .filterIsInstance<Leg.Transit>()
                        .flatMap { listOf(it.from, it.to) }
                        .mapNotNull { it.platform ?: it.scheduledPlatform }
                        .distinct()
                        .associateWith { getString(Res.string.platform_short, it) }
                val event =
                    CalendarEvent(
                        title = getString(Res.string.calendar_title, destinationName),
                        start = itinerary.start,
                        end = itinerary.end,
                        location = itinerary.legs.filterIsInstance<Leg.Transit>().firstOrNull()?.from?.name,
                        description = itinerary.calendarDescription(platforms::getValue),
                    )
                if (!calendar.add(event)) snackbar.showSnackbar(getString(Res.string.no_calendar_app))
            }
        },
        modifier = modifier,
    ) {
        Icon(Icons.Outlined.Event, contentDescription = null)
        Text(stringResource(Res.string.add_to_calendar), modifier = Modifier.padding(start = 8.dp))
    }
}

/** Each ride as its line with the stops and times where it is boarded and left. */
internal fun Itinerary.calendarDescription(platform: (String) -> String): String =
    legs.filterIsInstance<Leg.Transit>().joinToString("\n\n") { ride ->
        val line = listOfNotNull(ride.line.label, ride.headsign?.let { "→ $it" }).joinToString(" ")
        listOf(line, ride.from.calendarLine(ride.start.scheduled, platform), ride.to.calendarLine(ride.end.scheduled, platform))
            .joinToString("\n")
    }

private fun Waypoint.calendarLine(
    time: Instant,
    platform: (String) -> String,
): String = listOfNotNull("${clockTime(time)} $name", (this.platform ?: scheduledPlatform)?.let(platform)).joinToString(", ")
