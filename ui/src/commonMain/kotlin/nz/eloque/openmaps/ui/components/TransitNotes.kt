package nz.eloque.openmaps.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoveDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WrongLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nz.eloque.openmaps.core.model.Alert
import nz.eloque.openmaps.core.model.AlertEffect
import nz.eloque.openmaps.core.model.AlertSeverity
import nz.eloque.openmaps.core.model.Leg
import nz.eloque.openmaps.core.model.Waypoint
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.alert_accessibility
import nz.eloque.openmaps.ui.resources.alert_additional_service
import nz.eloque.openmaps.ui.resources.alert_delays
import nz.eloque.openmaps.ui.resources.alert_detour
import nz.eloque.openmaps.ui.resources.alert_from
import nz.eloque.openmaps.ui.resources.alert_modified_service
import nz.eloque.openmaps.ui.resources.alert_more
import nz.eloque.openmaps.ui.resources.alert_no_service
import nz.eloque.openmaps.ui.resources.alert_reduced_service
import nz.eloque.openmaps.ui.resources.alert_stop_moved
import nz.eloque.openmaps.ui.resources.alert_until
import nz.eloque.openmaps.ui.resources.alternative_from
import nz.eloque.openmaps.ui.resources.alternative_to
import nz.eloque.openmaps.ui.resources.alternatives
import nz.eloque.openmaps.ui.resources.reservation_required
import nz.eloque.openmaps.ui.resources.stay_seated
import nz.eloque.openmaps.ui.resources.stop_boarding_only
import nz.eloque.openmaps.ui.resources.stop_cancelled
import nz.eloque.openmaps.ui.resources.stop_exit_only
import nz.eloque.openmaps.ui.resources.stop_passed
import nz.eloque.openmaps.ui.resources.tickets
import nz.eloque.openmaps.ui.resources.towards
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** A disruption as a card coloured by how badly it hits the trip, its full text and link a tap away. */
@Composable
fun AlertNote(
    alert: Alert,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(alert.header) { mutableStateOf(false) }
    val tint = alert.tint
    val effect = alert.effect.label?.let { stringResource(it) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().clickable { expanded = !expanded },
    ) {
        Row(Modifier.padding(10.dp).animateContentSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(alert.effect.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                effect?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = tint) }
                Text(
                    alert.header,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                alert.period()?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (expanded) {
                    alert.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                    alert.url?.let { url ->
                        val uriHandler = LocalUriHandler.current
                        TextButton(onClick = { uriHandler.openUri(url) }, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(Res.string.alert_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Alert.period(): String? {
    val now = Clock.System.now()
    val start = start
    val end = end
    return when {
        start != null && start > now && end != null -> timeRange(start, end)
        start != null && start > now -> stringResource(Res.string.alert_from, dayTime(start))
        end != null && end > now -> stringResource(Res.string.alert_until, dayTime(end))
        else -> null
    }
}

private val Alert.tint: Color
    @Composable get() =
        when {
            severity == AlertSeverity.SEVERE || effect == AlertEffect.NO_SERVICE -> MaterialTheme.colorScheme.error
            severity == AlertSeverity.INFO || effect == AlertEffect.ADDITIONAL_SERVICE -> MaterialTheme.colorScheme.primary
            else -> Caution
        }

private val Caution = Color(0xFFE08A00)

private val AlertEffect.icon: ImageVector
    get() =
        when (this) {
            AlertEffect.NO_SERVICE -> Icons.Default.Block
            AlertEffect.DELAYS -> Icons.Default.Schedule
            AlertEffect.DETOUR, AlertEffect.MODIFIED_SERVICE -> Icons.Default.SyncAlt
            AlertEffect.STOP_MOVED -> Icons.Default.WrongLocation
            AlertEffect.ACCESSIBILITY -> Icons.AutoMirrored.Filled.Accessible
            AlertEffect.ADDITIONAL_SERVICE -> Icons.Default.AddCircleOutline
            AlertEffect.REDUCED_SERVICE, AlertEffect.OTHER -> Icons.Default.Warning
        }

private val AlertEffect.label: StringResource?
    get() =
        when (this) {
            AlertEffect.NO_SERVICE -> Res.string.alert_no_service
            AlertEffect.REDUCED_SERVICE -> Res.string.alert_reduced_service
            AlertEffect.DELAYS -> Res.string.alert_delays
            AlertEffect.DETOUR -> Res.string.alert_detour
            AlertEffect.STOP_MOVED -> Res.string.alert_stop_moved
            AlertEffect.ACCESSIBILITY -> Res.string.alert_accessibility
            AlertEffect.MODIFIED_SERVICE -> Res.string.alert_modified_service
            AlertEffect.ADDITIONAL_SERVICE -> Res.string.alert_additional_service
            AlertEffect.OTHER -> null
        }

/** Why passengers cannot use this stop as usual, or null where they can. */
@Composable
fun Waypoint.ruleText(): String? =
    when {
        isCancelled -> stringResource(Res.string.stop_cancelled)
        !canBoard && !canAlight -> stringResource(Res.string.stop_passed)
        !canBoard -> stringResource(Res.string.stop_exit_only)
        !canAlight -> stringResource(Res.string.stop_boarding_only)
        else -> null
    }

/** Where the vehicle carries on as other lines, the reservation it needs and where to buy a ticket. */
@Composable
fun RideNotes(
    leg: Leg.Transit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        leg.continuations.forEach { continuation ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.EventSeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(stringResource(Res.string.stay_seated, continuation.at), style = MaterialTheme.typography.bodySmall)
                LineChip(continuation.line, minWidth = 0.dp)
                continuation.headsign?.let {
                    Text(
                        stringResource(Res.string.towards, it.withoutLinePrefix(continuation.line)),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (leg.reservationRequired) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Caution, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.reservation_required), style = MaterialTheme.typography.bodySmall)
            }
        }
        leg.ticketUrl?.let { url ->
            val uriHandler = LocalUriHandler.current
            TextButton(onClick = { uriHandler.openUri(url) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    listOfNotNull(stringResource(Res.string.tickets), leg.line.agency).joinToString(" · "),
                    modifier = Modifier.padding(start = 8.dp),
                )
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(14.dp))
            }
        }
    }
}

/** Other runs that fit the same gap in the journey, by departure time and line. */
@Composable
fun Alternatives(
    leg: Leg.Transit,
    modifier: Modifier = Modifier,
    onOpen: ((Leg.Transit) -> Unit)? = null,
) {
    if (leg.alternatives.isEmpty()) return
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Default.MoveDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(Res.string.alternatives),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.padding(start = 26.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            leg.alternatives.forEach { alternative ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable(enabled = onOpen != null && alternative.tripId != null) { onOpen?.invoke(alternative) },
                ) {
                    Text(
                        clockTime(alternative.start.actual),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (alternative.isCancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    LineChip(alternative.line, minWidth = 0.dp)
                    val from = alternative.from.name.takeIf { it != leg.from.name }
                    val to = alternative.to.name.takeIf { it != leg.to.name }
                    val stops =
                        listOfNotNull(
                            from?.let { stringResource(Res.string.alternative_from, it) },
                            to?.let { stringResource(Res.string.alternative_to, it) },
                        )
                    if (stops.isNotEmpty()) {
                        Text(
                            stops.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
