package nz.eloque.openmaps.map

import kotlinx.coroutines.flow.first
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.offline.OfflineStorageState
import org.maplibre.compose.offline.offlineStorage

/** Keeps up to [megabytes] of viewed tiles, dropping the least recently used beyond that. Downloaded areas do not count. */
internal suspend fun setTileCacheSize(megabytes: Int) {
    val storage = DefaultMapRuntime.instance.offlineStorage
    storage.state.first { it is OfflineStorageState.Ready }
    storage.setMaximumAmbientCacheSize(megabytes * BYTES_PER_MEGABYTE)
}

/** Forgets all viewed tiles. Downloaded areas stay. */
suspend fun clearTileCache() {
    val storage = DefaultMapRuntime.instance.offlineStorage
    storage.state.first { it is OfflineStorageState.Ready }
    storage.clearAmbientCache()
}

private const val BYTES_PER_MEGABYTE = 1024L * 1024
