package nz.eloque.openmaps.ui.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nz.eloque.openmaps.map.OfflineArea
import nz.eloque.openmaps.map.OfflineAreaState
import nz.eloque.openmaps.map.OfflineMaps
import nz.eloque.openmaps.ui.components.Page
import nz.eloque.openmaps.ui.components.formatDate
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.delete
import nz.eloque.openmaps.ui.resources.offline_area_complete
import nz.eloque.openmaps.ui.resources.offline_area_downloading
import nz.eloque.openmaps.ui.resources.offline_area_failed
import nz.eloque.openmaps.ui.resources.offline_area_outdated
import nz.eloque.openmaps.ui.resources.offline_area_paused
import nz.eloque.openmaps.ui.resources.offline_area_too_large
import nz.eloque.openmaps.ui.resources.offline_download_area
import nz.eloque.openmaps.ui.resources.offline_empty
import nz.eloque.openmaps.ui.resources.offline_explanation
import nz.eloque.openmaps.ui.resources.offline_maps
import nz.eloque.openmaps.ui.resources.offline_pause
import nz.eloque.openmaps.ui.resources.offline_resume
import nz.eloque.openmaps.ui.resources.offline_update
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** Map areas kept on the device, with their downloads. */
@Composable
fun OfflineMapsPage(
    onBack: () -> Unit,
    onDownloadArea: () -> Unit,
) = Page(stringResource(Res.string.offline_maps), onBack) {
    val offline = koinInject<OfflineMaps>()
    val areas by offline.areas.collectAsState()
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Text(
            stringResource(Res.string.offline_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        Button(
            onClick = onDownloadArea,
            enabled = areas.none { it.state == OfflineAreaState.DOWNLOADING },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Text(stringResource(Res.string.offline_download_area), modifier = Modifier.padding(start = 8.dp))
        }
        HorizontalDivider(Modifier.padding(top = 16.dp))
        if (areas.isEmpty()) {
            Text(
                stringResource(Res.string.offline_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn {
            items(areas) { area ->
                AreaRow(
                    area,
                    canStart = areas.none { it !== area && it.state == OfflineAreaState.DOWNLOADING },
                    onPause = { offline.pause(area) },
                    onResume = { offline.resume(area) },
                    onUpdate = { offline.update(area) },
                    onDelete = { offline.delete(area) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AreaRow(
    area: OfflineArea,
    canStart: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(area.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    area.summary(),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (area.state == OfflineAreaState.TOO_LARGE || area.state == OfflineAreaState.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            when (area.state) {
                OfflineAreaState.DOWNLOADING ->
                    IconButton(onClick = onPause) { Icon(Icons.Default.Pause, stringResource(Res.string.offline_pause)) }
                OfflineAreaState.PAUSED, OfflineAreaState.FAILED ->
                    IconButton(onClick = onResume, enabled = canStart) {
                        Icon(Icons.Default.PlayArrow, stringResource(Res.string.offline_resume))
                    }
                OfflineAreaState.COMPLETE ->
                    IconButton(onClick = onUpdate, enabled = canStart) {
                        Icon(Icons.Default.Refresh, stringResource(Res.string.offline_update))
                    }
                OfflineAreaState.TOO_LARGE -> Unit
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(Res.string.delete)) }
        }
        if (area.state == OfflineAreaState.DOWNLOADING) {
            val progress = area.progress
            if (progress == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp))
            } else {
                LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp))
            }
        }
    }
}

@Composable
private fun OfflineArea.summary(): String {
    val size = "${(bytes / 100_000.0).roundToInt() / 10.0} MB"
    return when (state) {
        OfflineAreaState.DOWNLOADING -> stringResource(Res.string.offline_area_downloading, size)
        OfflineAreaState.PAUSED -> stringResource(Res.string.offline_area_paused, size)
        OfflineAreaState.TOO_LARGE -> stringResource(Res.string.offline_area_too_large)
        OfflineAreaState.FAILED -> stringResource(Res.string.offline_area_failed)
        OfflineAreaState.COMPLETE -> {
            val completed = completedAt
            val date = completed?.let { formatDate(it.toLocalDateTime(TimeZone.currentSystemDefault()).date) }.orEmpty()
            if (completed != null && Clock.System.now() - completed > OUTDATED_AFTER) {
                stringResource(Res.string.offline_area_outdated, size, date)
            } else {
                stringResource(Res.string.offline_area_complete, size, date)
            }
        }
    }
}

// The map server publishes a new map every week under new addresses. Once the app has been online since then, an older download no longer shows.
private val OUTDATED_AFTER = 7.days
