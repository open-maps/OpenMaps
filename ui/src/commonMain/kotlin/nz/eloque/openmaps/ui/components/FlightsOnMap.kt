package nz.eloque.openmaps.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.AirTraffic
import nz.eloque.openmaps.core.model.Aircraft
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.RequestsExhausted
import nz.eloque.openmaps.map.AircraftMarker
import nz.eloque.openmaps.map.MapController
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The aircraft last received, shared by the flights layer and the open aircraft sheet. */
class FlightTracker {
    private val _aircraft = MutableStateFlow<Map<String, Aircraft>>(emptyMap())
    val aircraft: StateFlow<Map<String, Aircraft>> = _aircraft.asStateFlow()

    /** The aircraft whose sheet is open, drawn highlighted and kept while the layer is off. */
    val selected = MutableStateFlow<String?>(null)

    /** Replaces all aircraft but the selected one, which stays until fresher news of it arrives. */
    fun replaceArea(aircraft: List<Aircraft>) {
        val fresh = aircraft.associateBy { it.id }
        val kept = selected.value?.takeIf { it !in fresh }?.let(_aircraft.value::get)
        _aircraft.value = if (kept == null) fresh else fresh + (kept.id to kept)
    }

    fun put(aircraft: Aircraft) = _aircraft.update { it + (aircraft.id to aircraft) }
}

/** Moves aircraft across the map while [on] and zoomed in, asking again every [REFRESH] or when the view leaves the area fetched. */
@Composable
fun FlightsOnMap(
    map: MapController,
    on: Boolean,
    onOpen: (id: String) -> Unit,
) {
    val traffic = koinInject<AirTraffic>()
    val tracker = koinInject<FlightTracker>()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(map, onOpen) { map.onAircraftClick = { onOpen(it.id) } }
    LaunchedEffect(map, on) {
        if (!on) tracker.replaceArea(emptyList())
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                while (true) {
                    val now = Clock.System.now()
                    val visible = map.visibleBounds
                    val showAll = on && map.zoom >= MIN_ZOOM
                    val selected = tracker.selected.value
                    map.aircraft =
                        tracker.aircraft.value.values.mapNotNull { plane ->
                            val location = plane.positionAt(now)
                            val shown = plane.id == selected || (showAll && visible != null && location in visible)
                            if (shown) plane.toMarker(location, plane.id == selected) else null
                        }
                    delay(TICK)
                }
            }
            if (on) launch { fetchVisible(map, traffic, tracker) }
        }
    }
}

private suspend fun fetchVisible(
    map: MapController,
    traffic: AirTraffic,
    tracker: FlightTracker,
) {
    var area: Pair<LatLng, Double>? = null
    var fetched = Instant.DISTANT_PAST
    var waitUntil = Instant.DISTANT_PAST
    snapshotFlow { if (map.zoom < MIN_ZOOM) null else map.visibleBounds?.let { it to map.isMoving } }
        .distinctUntilChanged()
        .collectLatest { view ->
            if (view == null) {
                tracker.replaceArea(emptyList())
                return@collectLatest
            }
            val (visible, moving) = view
            while (true) {
                val now = Clock.System.now()
                val covered = area?.let { (center, radius) -> visible.corners().all { center.distanceTo(it) <= radius } } == true
                if (!moving && (!covered || now - fetched >= REFRESH) && now >= waitUntil) {
                    val center = visible.center()
                    val radius = (visible.corners().maxOf { center.distanceTo(it) } * PADDING).coerceAtMost(MAX_RADIUS_METERS)
                    try {
                        tracker.replaceArea(traffic.near(center, radius))
                        area = center to radius
                        fetched = now
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        waitUntil = now + e.backoff()
                    }
                }
                delay(TICK)
            }
        }
}

/** How long to leave the server alone after [this] failure, longer once the daily allowance is used up. */
internal fun Exception.backoff(): Duration = if (this is RequestsExhausted) QUOTA_BACKOFF else RETRY

private fun Aircraft.toMarker(
    location: LatLng,
    selected: Boolean,
) = AircraftMarker(id, callsign ?: registration, location, trackDegrees, onGround, selected)

private fun GeoBounds.corners() = listOf(LatLng(south, west), LatLng(south, east), LatLng(north, west), LatLng(north, east))

private fun GeoBounds.center() = LatLng((south + north) / 2, (west + east) / 2)

private const val MIN_ZOOM = 7.0
private const val PADDING = 1.3
private const val MAX_RADIUS_METERS = 463_000.0
private val REFRESH = 30.seconds
private val TICK = 1.seconds
private val RETRY = 30.seconds
private val QUOTA_BACKOFF = 30.minutes
