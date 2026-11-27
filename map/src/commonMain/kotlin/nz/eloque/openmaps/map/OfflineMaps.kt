package nz.eloque.openmaps.map

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nz.eloque.openmaps.core.model.GeoBounds
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.offline.DownloadProgress
import org.maplibre.compose.offline.DownloadStatus
import org.maplibre.compose.offline.OfflinePack
import org.maplibre.compose.offline.OfflinePackDefinition
import org.maplibre.compose.offline.OfflineStorageState
import org.maplibre.compose.offline.offlineStorage
import org.maplibre.compose.resource.MapResourceError
import org.maplibre.compose.resource.MapResourceLoad
import org.maplibre.compose.resource.MapResourceLoadRequest
import org.maplibre.compose.resource.MapResourceProvider
import org.maplibre.compose.resource.MapResourceRequest
import org.maplibre.spatialk.geojson.BoundingBox
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan
import kotlin.time.Clock
import kotlin.time.Instant

/** What a map server answered for one file of a download. */
class FetchedFile(
    /** Null for 304 Not Modified and 204 No Content. */
    val bytes: ByteArray?,
    val status: Int,
    val etag: String?,
    val expires: Instant?,
    val modified: Instant?,
)

/** Fetches [url], sending [etag] for revalidation when given. */
fun interface MapFileFetcher {
    suspend fun fetch(
        url: String,
        etag: String?,
    ): FetchedFile
}

enum class OfflineAreaState { DOWNLOADING, PAUSED, COMPLETE, TOO_LARGE, FAILED }

/** A map area kept on the device for use without a connection. */
class OfflineArea internal constructor(
    val name: String,
    val bounds: GeoBounds,
    val state: OfflineAreaState,
    /** Between 0 and 1, or null while the number of files is still unknown. */
    val progress: Float?,
    val bytes: Long,
    /** When the download last finished, null before it has. */
    val completedAt: Instant?,
    internal val pack: OfflinePack,
)

@Serializable
private data class AreaInfo(
    val name: String,
    val completedAt: Long? = null,
    val tooLarge: Boolean = false,
)

/**
 * Downloads of the base map that keep within what the map server allows, one area at a time.
 * [install] must run before the first map is shown.
 */
class OfflineMaps(
    private val fetcher: MapFileFetcher,
    private val scope: CoroutineScope,
) {
    private val permits = Semaphore(MAX_PARALLEL_REQUESTS)

    // Downloads must not exceed the server's parallel request limit. Viewing the map is not limited.
    private val provider =
        object : MapResourceProvider {
            override fun accepts(request: MapResourceRequest) = request.url.startsWith(SERVER)

            override suspend fun load(request: MapResourceLoadRequest): MapResourceLoad =
                if (request.usage == MapResourceLoadRequest.Usage.Offline) permits.withPermit { loadFile(request) } else loadFile(request)
        }

    fun install() {
        DefaultMapRuntime.configure { resourceProvider = provider }
        scope.launch {
            packs().collect { list ->
                _areas.value = list
                watch(list)
            }
        }
    }

    private val storage get() = DefaultMapRuntime.instance.offlineStorage

    private val _areas = MutableStateFlow<List<OfflineArea>>(emptyList())
    val areas: StateFlow<List<OfflineArea>> = _areas.asStateFlow()

    private fun packs(): Flow<List<OfflineArea>> =
        storage.state.flatMapLatest { state ->
            val packs = (state as? OfflineStorageState.Ready)?.packs.orEmpty().toList()
            if (packs.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(packs.map { pack -> combine(pack.metadata, pack.downloadProgress) { _, _ -> pack.toArea() } }) {
                    it.toList().sortedBy { area -> area.name }
                }
            }
        }

    fun download(
        name: String,
        bounds: GeoBounds,
        pixelRatio: Float,
    ) = scope.launch {
        val definition =
            OfflinePackDefinition.TilePyramid(
                LIGHT_STYLE,
                BoundingBox(bounds.west, bounds.south, bounds.east, bounds.north),
                pixelRatio,
                0.0,
                MAX_ZOOM.toDouble(),
            )
        val pack = ready().create(definition, encode(AreaInfo(name)))
        ready().resume(pack)
    }

    fun pause(area: OfflineArea) = storage.pause(area.pack)

    fun resume(area: OfflineArea) {
        storage.resume(area.pack)
    }

    /** Downloads the area again, as the map server publishes a new map every week under new addresses. */
    fun update(area: OfflineArea) =
        scope.launch {
            val pack = area.pack
            pack.setMetadata(encode(pack.info().copy(completedAt = null, tooLarge = false)))
            ready().invalidate(pack)
            resume(area)
        }

    fun delete(area: OfflineArea) = scope.launch { ready().delete(area.pack) }

    private suspend fun ready() = storage.also { s -> s.state.first { it is OfflineStorageState.Ready } }

    private suspend fun watch(areas: List<OfflineArea>) {
        for (area in areas) {
            val pack = area.pack
            val info = pack.info()
            when {
                area.state == OfflineAreaState.DOWNLOADING && area.bytes > MAX_DOWNLOAD_BYTES -> {
                    storage.pause(pack)
                    pack.setMetadata(encode(info.copy(tooLarge = true)))
                }
                area.state == OfflineAreaState.COMPLETE && info.completedAt == null ->
                    pack.setMetadata(encode(info.copy(completedAt = Clock.System.now().toEpochMilliseconds())))
            }
        }
    }

    private suspend fun loadFile(request: MapResourceLoadRequest): MapResourceLoad {
        if (request.loadingMethod == MapResourceLoadRequest.LoadingMethod.CacheOnly) {
            return MapResourceLoad.Failed(MapResourceError.Connection, "not cached")
        }
        val file =
            try {
                fetcher.fetch(request.url, request.priorEtag)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return MapResourceLoad.Failed(MapResourceError.Connection, e.message ?: "connection failed")
            }
        return when {
            file.status == HTTP_NOT_MODIFIED -> MapResourceLoad.NotModified(file.modified, file.expires)
            file.status == HTTP_NO_CONTENT || file.status == HTTP_NOT_FOUND -> MapResourceLoad.NoContent(file.modified, file.expires)
            file.status == HTTP_TOO_MANY_REQUESTS -> MapResourceLoad.Failed(MapResourceError.RateLimit, "rate limited")
            file.status in 200..299 && file.bytes != null ->
                MapResourceLoad.Bytes(file.bytes, file.etag, false, file.modified, file.expires)
            file.status >= HTTP_SERVER_ERROR -> MapResourceLoad.Failed(MapResourceError.Server, "HTTP ${file.status}")
            else -> MapResourceLoad.Failed(MapResourceError.Other, "HTTP ${file.status}")
        }
    }
}

private fun OfflinePack.info(): AreaInfo =
    metadata.value?.let { bytes -> runCatching { Json.decodeFromString<AreaInfo>(bytes.decodeToString()) }.getOrNull() } ?: AreaInfo("")

private fun encode(info: AreaInfo) = Json.encodeToString(info).encodeToByteArray()

private fun OfflinePack.toArea(): OfflineArea {
    val info = info()
    val box = (definition as? OfflinePackDefinition.TilePyramid)?.bounds
    val progress = downloadProgress.value
    val healthy = progress as? DownloadProgress.Healthy
    val state =
        when {
            info.tooLarge -> OfflineAreaState.TOO_LARGE
            progress is DownloadProgress.Error || progress is DownloadProgress.TileLimitExceeded -> OfflineAreaState.FAILED
            healthy?.status is DownloadStatus.Complete -> OfflineAreaState.COMPLETE
            healthy?.status is DownloadStatus.Downloading -> OfflineAreaState.DOWNLOADING
            else -> OfflineAreaState.PAUSED
        }
    val fraction =
        healthy
            ?.takeIf { it.isRequiredResourceCountPrecise && it.requiredResourceCount > 0 }
            ?.let { it.completedResourceCount.toFloat() / it.requiredResourceCount }
    return OfflineArea(
        name = info.name,
        bounds = box?.let { GeoBounds(it.south, it.west, it.north, it.east) } ?: GeoBounds(0.0, 0.0, 0.0, 0.0),
        state = state,
        progress = fraction,
        bytes = healthy?.completedResourceBytes ?: 0,
        completedAt = info.completedAt?.let(Instant::fromEpochMilliseconds),
        pack = this,
    )
}

/** How many map tiles a download of [bounds] needs, counting each layer of the map style. */
fun tileCount(bounds: GeoBounds): Int = tilesUpTo(bounds, MAX_ZOOM) + tilesUpTo(bounds, RELIEF_MAX_ZOOM)

private fun tilesUpTo(
    bounds: GeoBounds,
    maxZoom: Int,
): Int =
    (0..maxZoom).sumOf { zoom ->
        val columns = tileX(bounds.east, zoom) - tileX(bounds.west, zoom) + 1
        val rows = tileY(bounds.south, zoom) - tileY(bounds.north, zoom) + 1
        columns * rows
    }

private fun tileX(
    longitude: Double,
    zoom: Int,
): Int = floor((longitude + 180) / 360 * (1 shl zoom)).toInt().coerceIn(0, (1 shl zoom) - 1)

private fun tileY(
    latitude: Double,
    zoom: Int,
): Int {
    val radians = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE) * PI / 180
    val y = (1 - ln(tan(radians) + 1 / cos(radians)) / PI) / 2 * (1 shl zoom)
    return floor(y).toInt().coerceIn(0, (1 shl zoom) - 1)
}

/** OpenFreeMap allows offline downloads of at most this many tiles and bytes each, with at most this many parallel requests. */
const val MAX_DOWNLOAD_TILES = 3_000
const val MAX_DOWNLOAD_BYTES = 50L * 1_000_000
private const val MAX_PARALLEL_REQUESTS = 4

private const val SERVER = "https://tiles.openfreemap.org/"
private const val MAX_ZOOM = 14
private const val RELIEF_MAX_ZOOM = 6
private const val MAX_LATITUDE = 85.05112878
private const val HTTP_NO_CONTENT = 204
private const val HTTP_NOT_MODIFIED = 304
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR = 500
