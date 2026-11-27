package nz.eloque.openmaps.ui.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import nz.eloque.openmaps.core.model.Geocoder
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.map.MAX_DOWNLOAD_TILES
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.map.OfflineAreaState
import nz.eloque.openmaps.map.OfflineMaps
import nz.eloque.openmaps.map.tileCount
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.offline_area_name
import nz.eloque.openmaps.ui.resources.offline_busy
import nz.eloque.openmaps.ui.resources.offline_download
import nz.eloque.openmaps.ui.resources.offline_download_area
import nz.eloque.openmaps.ui.resources.offline_frame_hint
import nz.eloque.openmaps.ui.resources.offline_not_included
import nz.eloque.openmaps.ui.resources.offline_tiles
import nz.eloque.openmaps.ui.resources.offline_too_large
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Downloads the map area on screen, once it is small enough for the map server's limits. */
@Composable
fun DownloadAreaSheet(
    map: MapController,
    onStarted: () -> Unit,
) {
    val offline = koinInject<OfflineMaps>()
    val geocoder = koinInject<Geocoder>()
    val pixelRatio = LocalDensity.current.density
    val bounds by remember(map) { derivedStateOf { map.visibleBounds?.takeUnless { map.isMoving } } }
    val tiles = bounds?.let(::tileCount)
    val areas by offline.areas.collectAsState()
    val downloading = areas.any { it.state == OfflineAreaState.DOWNLOADING }
    var name by rememberSaveable { mutableStateOf("") }
    var named by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(bounds) {
        val area = bounds ?: return@LaunchedEffect
        if (named) return@LaunchedEffect
        val center = LatLng((area.south + area.north) / 2, (area.west + area.east) / 2)
        val place =
            try {
                geocoder.reverse(center)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        place?.let { name = it.area ?: it.name }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.offline_download_area), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(Res.string.offline_frame_hint), style = MaterialTheme.typography.bodyMedium)
        val tooLarge = tiles != null && tiles > MAX_DOWNLOAD_TILES
        tiles?.let {
            Text(
                if (tooLarge) {
                    stringResource(
                        Res.string.offline_too_large,
                    )
                } else {
                    stringResource(Res.string.offline_tiles, it, MAX_DOWNLOAD_TILES)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (tooLarge) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                named = true
            },
            label = { Text(stringResource(Res.string.offline_area_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (downloading) Text(stringResource(Res.string.offline_busy), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = {
                val area = bounds ?: return@Button
                offline.download(name.trim(), area, pixelRatio)
                onStarted()
            },
            enabled = bounds != null && !tooLarge && !downloading && name.isNotBlank(),
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Text(stringResource(Res.string.offline_download), modifier = Modifier.padding(start = 8.dp))
        }
        Text(
            stringResource(Res.string.offline_not_included),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}
