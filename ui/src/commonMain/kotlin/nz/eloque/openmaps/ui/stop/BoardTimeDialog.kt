package nz.eloque.openmaps.ui.stop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import nz.eloque.openmaps.ui.components.Weekdays
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.cancel
import nz.eloque.openmaps.ui.resources.ok
import nz.eloque.openmaps.ui.resources.today
import nz.eloque.openmaps.ui.resources.tomorrow
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

/** Picks a day in the coming week and a time for the board to start at. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardTimeDialog(
    initial: Instant?,
    onDismiss: () -> Unit,
    onPick: (Instant) -> Unit,
) {
    val zone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(zone).date
    val start = (initial ?: Clock.System.now()).toLocalDateTime(zone)
    var dayOffset by rememberSaveable { mutableStateOf((start.date.toEpochDays() - today.toEpochDays()).toInt().coerceIn(0, DAYS - 1)) }
    val time = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(today.plus(DatePeriod(days = dayOffset)).atTime(LocalTime(time.hour, time.minute)).toInstant(zone))
            }) {
                Text(stringResource(Res.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(DAYS) { offset ->
                        FilterChip(
                            selected = offset == dayOffset,
                            onClick = { dayOffset = offset },
                            label = { Text(dayName(today.plus(DatePeriod(days = offset)), today)) },
                        )
                    }
                }
                TimePicker(time)
            }
        },
    )
}

/** Today, Tomorrow or the weekday, for days in the coming week. */
@Composable
fun dayName(
    date: LocalDate,
    today: LocalDate,
): String =
    when (date.toEpochDays() - today.toEpochDays()) {
        0L -> stringResource(Res.string.today)
        1L -> stringResource(Res.string.tomorrow)
        else -> stringResource(Weekdays[date.dayOfWeek.ordinal])
    }

private const val DAYS = 7
