package nz.eloque.openmaps.map

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.UiComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import nz.eloque.openmaps.core.model.GeoBounds
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.LoadedPlaces
import nz.eloque.openmaps.core.model.MapLayer
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.RadarFrames
import nz.eloque.openmaps.core.model.RoadEventTiles
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.TransportMode
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.IconPitchAlignment
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FeaturesClickHandler
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.VectorTileSourceHandle
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** A line drawn on the map, such as one leg of a route. */
@Immutable
data class MapLine(
    val points: List<LatLng>,
    val color: Color,
    val width: Dp = 6.dp,
)

/** The user's position while navigating, drawn as an arrow turned to [bearing]. */
@Immutable
data class NavigationPuck(
    val position: LatLng,
    val bearing: Double?,
)

/** Map camera, user location, the highlighted marker and drawn lines, shared between [OpenMap] and the UI around it. */
@Stable
class MapController internal constructor(
    internal val mapState: MapState,
    internal val location: LocationState,
    private val markerState: MutableState<LatLng?>,
    private val linesState: MutableState<List<MapLine>>,
    private val stopsState: MutableState<List<SearchResult.Stop>>,
    private val resultsState: MutableState<List<SearchResult.Place>>,
    private val resultIconState: MutableState<ImageVector?>,
    private val layersState: MutableState<Set<MapLayer>>,
    private val sharingState: MutableState<List<SharingPin>>,
    private val routesState: MutableState<List<MapRoute>>,
    private val roadEventsState: MutableState<RoadEventTiles?>,
    private val puckState: MutableState<NavigationPuck?>,
    private val parkedCarState: MutableState<LatLng?>,
    private val measuringState: MutableState<List<LatLng>?>,
    private val recordingState: MutableState<List<LatLng>>,
    private val radarState: MutableState<RadarFrames?>,
    private val radarFrameState: MutableState<Int>,
    private val cloudFrameState: MutableState<Int>,
    private val vehiclesState: MutableState<List<VehicleMarker>>,
    private val aircraftState: MutableState<List<AircraftMarker>>,
) {
    val isLocationPermissionGranted: Boolean
        get() = location.permission is LocationPermission.Granted

    val userLocation: LatLng?
        get() = location.lastLocation?.position?.toLatLng()

    val hasLocationFix: Boolean
        get() = location.lastLocation != null

    /** The highlighted place, or null for none. */
    var marker: LatLng? by markerState

    var lines: List<MapLine> by linesState

    var onLongPress: ((LatLng) -> Unit)? by mutableStateOf(null)

    /** A button floating on the map below the compass, set by the screen that needs it. */
    var floatingAction: MapAction? by mutableStateOf(null)

    /** Stops drawn as tappable dots. */
    var stops: List<SearchResult.Stop> by stopsState

    var onStopClick: ((SearchResult.Stop) -> Unit)? by mutableStateOf(null)

    /** Places tapped on the map or among [results]. */
    var onPlaceClick: ((SearchResult.Place) -> Unit)? by mutableStateOf(null)

    /** Search results drawn as pins. */
    var results: List<SearchResult.Place> by resultsState

    /** Drawn on every result pin in place of its category icon, as for the places of a list. */
    var resultIcon: ImageVector? by resultIconState

    /** Optional layers drawn with the base map. */
    var layers: Set<MapLayer> by layersState

    /** The button for choosing [layers], shown where [floatingAction] goes while that is unset. */
    var layersAction: MapAction? by mutableStateOf(null)

    /** Points of a distance being measured, joined up to the crosshair at the map center, or null when not measuring. */
    var measuring: List<LatLng>? by measuringState

    /** The track being recorded, drawn under everything else on the map. */
    var recording: List<LatLng> by recordingState

    /** Where the user left the car, drawn as a badge. */
    var parkedCar: LatLng? by parkedCarState

    var onParkedCarClick: (() -> Unit)? by mutableStateOf(null)

    /** Shared vehicles and their stations. */
    var sharing: List<SharingPin> by sharingState

    var onSharingClick: ((String) -> Unit)? by mutableStateOf(null)

    /** Routes to choose between, drawn in grey with a label each. */
    var routes: List<MapRoute> by routesState

    var onRouteClick: ((String) -> Unit)? by mutableStateOf(null)

    var puck: NavigationPuck? by puckState

    /** True while the user drags, pinches or rotates the map. */
    val isMovedByUser: Boolean
        get() = mapState.isCameraMoving && mapState.cameraMoveReason == CameraMoveReason.Gesture

    /** Moves the camera smoothly to [position], turned to [bearing] and tilted by [tilt] degrees. */
    suspend fun follow(
        position: LatLng,
        bearing: Double?,
        zoom: Double,
        tilt: Double,
    ) {
        mapState.animateCamera(
            CameraUpdate(center = position.toPosition(), zoom = zoom, bearing = bearing, pitch = tilt),
            CameraAnimation.Ease { duration = FollowAnimation },
        )
    }

    /** Receives the attribution of each source on the map as HTML, to show in full. */
    var onAttributionClick: ((List<String>) -> Unit)? by mutableStateOf(null)

    /** Rain radar frames, drawn while the radar layer is on. */
    var radar: RadarFrames? by radarState

    /** Index of the radar frame shown. */
    var radarFrame: Int by radarFrameState

    /** Index of the cloud picture shown while the radar is off. */
    var cloudFrame: Int by cloudFrameState

    /** Transit vehicles drawn where they are now, shown while [MapLayer.VEHICLES] is on. */
    var vehicles: List<VehicleMarker> by vehiclesState

    var onVehicleClick: ((VehicleMarker) -> Unit)? by mutableStateOf(null)

    /** Aircraft drawn where they are now. */
    var aircraft: List<AircraftMarker> by aircraftState

    var onAircraftClick: ((AircraftMarker) -> Unit)? by mutableStateOf(null)

    /** Road events drawn on the map, or null for none. */
    var roadEvents: RoadEventTiles? by roadEventsState

    /** Receives the tapped road event's properties, to read with [nz.eloque.openmaps.core.model.RoadEvents.event]. */
    var onRoadEventClick: ((Map<String, String>) -> Unit)? by mutableStateOf(null)

    /** Properties of the road events within [meters] of [route] in the tiles loaded so far. */
    suspend fun roadEventsAlong(
        route: List<LatLng>,
        meters: Double = ROUTE_EVENT_DISTANCE,
    ): List<Map<String, String>> {
        val tiles = roadEvents ?: return emptyList()
        if (route.isEmpty()) return emptyList()
        val source = mapState.style.sources[roadSourceId(tiles)] as? VectorTileSourceHandle ?: return emptyList()
        val south = route.minOf { it.latitude }
        val north = route.maxOf { it.latitude }
        val west = route.minOf { it.longitude }
        val east = route.maxOf { it.longitude }
        val margin = meters / 100_000
        return source
            .querySourceFeatures(setOf(tiles.layer), const(true))
            .filter { feature ->
                val box = feature.geometry?.bbox ?: return@filter true
                box.north >= south - margin && box.south <= north + margin && box.east >= west - margin && box.west <= east + margin
            }.filter { it.geometry?.near(route, meters) == true }
            .map { it.properties?.asText().orEmpty() }
            .distinctBy { it["id"] }
    }

    val center: LatLng
        get() = mapState.cameraPosition.center.toLatLng()

    val isMoving: Boolean
        get() = mapState.isCameraMoving

    val zoom: Double
        get() = mapState.cameraPosition.zoom

    /** Null until the map has been laid out. */
    val visibleBounds: GeoBounds?
        get() =
            mapState.viewport?.visibleBounds?.let {
                GeoBounds(it.southwest.latitude, it.southwest.longitude, it.northeast.latitude, it.northeast.longitude)
            }

    fun requestLocationPermission() = location.requestPermission()

    /** True once a place or route has been put in view, so the start no longer centers on the user. */
    var cameraPlaced = false
        private set

    /** Returns false when there is no location fix yet. */
    suspend fun centerOnUser(): Boolean {
        val position = location.lastLocation?.position ?: return false
        mapState.animateCamera(
            CameraUpdate(center = position, zoom = maxOf(mapState.cameraPosition.zoom, USER_ZOOM)),
        )
        return true
    }

    /** Centers on [target]. With [zoomIn] the camera also zooms to street level unless it is already closer. */
    suspend fun show(
        target: LatLng,
        zoomIn: Boolean = true,
    ) {
        cameraPlaced = true
        awaitEngaged()
        val current = mapState.cameraPosition.zoom
        mapState.animateCamera(CameraUpdate(center = target.toPosition(), zoom = if (zoomIn) maxOf(current, PLACE_ZOOM) else current))
    }

    /** Places of a category in the map tiles loaded so far, read again each time the map comes to rest. Includes unnamed ones. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val loadedPlaces: LoadedPlaces =
        LoadedPlaces { category ->
            val values = category.tileValues.toList()
            snapshotFlow { mapState.isCameraMoving }
                .filter { !it }
                .mapLatest {
                    delay(TILE_SETTLE_MILLIS)
                    tilePlaces(values)
                }
        }

    private suspend fun tilePlaces(values: List<String>): List<SearchResult.Place> {
        val tiles = mapState.style.sources[BASE_SOURCE] as? VectorTileSourceHandle ?: return emptyList()
        val matches =
            switch(
                feature["subclass"].asString(),
                cases = listOf(case(values, const(true))),
                fallback = switch(feature["class"].asString(), cases = listOf(case(values, const(true))), fallback = const(false)),
            )
        return tiles
            .querySourceFeatures(setOf("poi"), matches)
            .mapNotNull { it.toPlace(tapped = null, named = false) }
            .filter { place -> place.category in values || PlaceCategory.entries.none { place.category in it.tileValues } }
            .distinctBy { it.id ?: it.location }
    }

    /** A web map showing [location], for sharing places the data sources have no page for. */
    fun linkTo(location: LatLng): String {
        val (latitude, longitude) = location.latitude to location.longitude
        return "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=18/$latitude/$longitude"
    }

    // Camera moves before the map view is attached are dropped, as when a link starts the app.
    private suspend fun awaitEngaged() {
        withTimeoutOrNull(ENGAGE_TIMEOUT_MILLIS) {
            while (!mapState.isEngaged) delay(ENGAGE_POLL_MILLIS)
        }
    }

    suspend fun fit(points: List<LatLng>) {
        if (points.isEmpty()) return
        cameraPlaced = true
        awaitEngaged()
        val box =
            BoundingBox(
                west = points.minOf { it.longitude },
                south = points.minOf { it.latitude },
                east = points.maxOf { it.longitude },
                north = points.maxOf { it.latitude },
            )
        mapState.animateCameraToBounds(box, CameraFit(fitPadding = DpPadding(FIT_MARGIN, FIT_MARGIN, FIT_MARGIN, FIT_MARGIN)))
    }
}

/** [loadStyle] downloads a style document by URL, used to build the dark style from the light one. */
@Composable
@UiComposable
fun rememberMapController(
    loadStyle: suspend (String) -> String,
    /** Megabytes of viewed tiles kept on the device. */
    cacheMegabytes: Int,
    dark: Boolean = isSystemInDarkTheme(),
): MapController {
    val darkBase by produceState<BaseStyle?>(null, dark) {
        if (dark) darkStyles { loadStyle(LIGHT_STYLE) }.collect { value = it }
    }
    LaunchedEffect(cacheMegabytes) { setTileCacheSize(cacheMegabytes) }
    val location = rememberLocationState()
    val markerColor = MaterialTheme.colorScheme.primary
    val casingColor = if (dark) Color.Black else Color.White
    val markerState = remember { mutableStateOf<LatLng?>(null, referentialEqualityPolicy()) }
    val linesState = remember { mutableStateOf<List<MapLine>>(emptyList(), referentialEqualityPolicy()) }
    val stopsState = remember { mutableStateOf<List<SearchResult.Stop>>(emptyList()) }
    val resultsState = remember { mutableStateOf<List<SearchResult.Place>>(emptyList()) }
    val resultIconState = remember { mutableStateOf<ImageVector?>(null) }
    val layersState = remember { mutableStateOf(setOf(MapLayer.ROADWORKS, MapLayer.BUILDINGS_3D)) }
    val sharingState = remember { mutableStateOf<List<SharingPin>>(emptyList(), referentialEqualityPolicy()) }
    val routesState = remember { mutableStateOf<List<MapRoute>>(emptyList(), referentialEqualityPolicy()) }
    val roadEventsState = remember { mutableStateOf<RoadEventTiles?>(null) }
    val puckState = remember { mutableStateOf<NavigationPuck?>(null) }
    val parkedCarState = remember { mutableStateOf<LatLng?>(null) }
    val measuringState = remember { mutableStateOf<List<LatLng>?>(null, referentialEqualityPolicy()) }
    val recordingState = remember { mutableStateOf<List<LatLng>>(emptyList(), referentialEqualityPolicy()) }
    val radarState = remember { mutableStateOf<RadarFrames?>(null) }
    val radarFrameState = remember { mutableStateOf(0) }
    val cloudFrameState = remember { mutableStateOf(0) }
    val vehiclesState = remember { mutableStateOf<List<VehicleMarker>>(emptyList(), referentialEqualityPolicy()) }
    val aircraftState = remember { mutableStateOf<List<AircraftMarker>>(emptyList(), referentialEqualityPolicy()) }
    val puckIcon =
        rememberVectorPainter(Icons.Default.Navigation).let { painter ->
            image(remember(painter, markerColor) { BadgePainter(painter, markerColor, Color.White) }, size = DpSize(30.dp, 30.dp))
        }
    val routeColors =
        RouteColors(
            other = if (dark) Color(0xFF6B7280) else Color(0xFFB0B7C3),
            casing = casingColor,
            label = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelText = MaterialTheme.colorScheme.onSurface,
            selectedLabel = MaterialTheme.colorScheme.primary,
            selectedLabelText = MaterialTheme.colorScheme.onPrimary,
        )
    val controllerRef = remember { arrayOfNulls<MapController>(1) }
    val stopIcons =
        StopIcon.entries.associateWith { icon ->
            image(rememberVectorPainter(icon.vector), size = DpSize(14.dp, 14.dp), colorFilter = ColorFilter.tint(StopIconTint))
        }
    val mapState =
        rememberMapState(
            baseStyle = if (dark) darkBase ?: LoadingDarkStyle else BaseStyle.Uri(LIGHT_STYLE),
            initialCameraPosition = CameraPosition(center = EUROPE, zoom = 3.5),
        ) {
            OverlayLayers(layersState.value, dark)
            if (MapLayer.CLOUDS in layersState.value) {
                val radarShown = radarState.value?.takeIf { MapLayer.RAIN_RADAR in layersState.value }
                val (clouds, shown) =
                    cloudsToShow(
                        radarState.value?.clouds.orEmpty(),
                        radarShown,
                        radarFrameState.value,
                        cloudFrameState.value,
                    )
                CloudLayers(clouds, shown)
            }
            RadarLayers(radarState.value?.takeIf { MapLayer.RAIN_RADAR in layersState.value }, radarFrameState.value, dark)
            RoadLayers(roadEventsState.value, dark, MapLayer.ROADWORKS in layersState.value) { properties ->
                controllerRef[0]?.onRoadEventClick?.invoke(properties)
            }
            PlaceLayers(dark) { place -> controllerRef[0]?.onPlaceClick?.invoke(place) }
            ResultLayers(resultsState.value, resultIconState.value, dark) { place -> controllerRef[0]?.onPlaceClick?.invoke(place) }
            SharingLayers(sharingState.value, dark) { id -> controllerRef[0]?.onSharingClick?.invoke(id) }
            if (MapLayer.VEHICLES in layersState.value) {
                VehicleLayers(vehiclesState.value, dark) { controllerRef[0]?.onVehicleClick?.invoke(it) }
            }
            RecordingLayer(recordingState.value, markerColor)
            ParkedCarLayer(parkedCarState.value, dark) { controllerRef[0]?.onParkedCarClick?.invoke() }
            OtherRouteLines(routesState.value, routeColors) { id -> controllerRef[0]?.onRouteClick?.invoke(id) }
            linesState.value.filter { it.points.size >= 2 }.forEachIndexed { index, line ->
                key(index, line) {
                    // Old and new layers coexist briefly while the list changes, so ids must not repeat across contents.
                    val id = "$index-${line.hashCode()}"
                    val source = rememberGeoJsonSource(GeoJsonData.Features(LineString(line.points.map { it.toPosition() })))
                    LineLayer(
                        id = "line-casing-$id",
                        source = source,
                        color = const(casingColor),
                        width = const(line.width + 3.dp),
                        cap = const(LineCap.Round),
                        join = const(LineJoin.Round),
                    )
                    LineLayer(
                        id = "line-$id",
                        source = source,
                        color = const(line.color),
                        width = const(line.width),
                        cap = const(LineCap.Round),
                        join = const(LineJoin.Round),
                    )
                }
            }
            AircraftLayers(aircraftState.value, dark, markerColor) { controllerRef[0]?.onAircraftClick?.invoke(it) }
            val stops = stopsState.value
            if (stops.isNotEmpty()) {
                val features =
                    stops.mapIndexed { index, stop ->
                        Feature(
                            Point(stop.location.toPosition()),
                            buildJsonObject {
                                put(STOP_INDEX, index)
                                put(STOP_ICON, StopIcon.of(stop.modes).name)
                            },
                        )
                    }
                val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
                val onClick: FeaturesClickHandler = { clicked ->
                    val index = clicked.firstOrNull()?.properties?.get(STOP_INDEX)?.jsonPrimitive?.intOrNull
                    val stop = index?.let(stops::getOrNull)
                    val handler = controllerRef[0]?.onStopClick
                    if (stop == null || handler == null) {
                        ClickResult.Pass
                    } else {
                        handler(stop)
                        ClickResult.Consume
                    }
                }
                CircleLayer(
                    id = "stops",
                    source = source,
                    minZoom = STOPS_MIN_ZOOM.toFloat(),
                    color = const(Color.White),
                    radius = const(10.dp),
                    strokeColor = const(markerColor),
                    strokeWidth = const(2.dp),
                    onClick = onClick,
                )
                SymbolLayer(
                    id = "stop-icons",
                    source = source,
                    minZoom = STOPS_MIN_ZOOM.toFloat(),
                    iconImage =
                        switch(
                            feature[STOP_ICON],
                            cases = StopIcon.entries.map { case(it.name, stopIcons.getValue(it)) },
                            fallback = stopIcons.getValue(StopIcon.BUS),
                        ),
                    iconAllowOverlap = const(true),
                    onClick = onClick,
                )
            }
            LocationIndicatorLayer(id = "user-location", locationState = location)
            markerState.value?.let { marker ->
                val source = rememberGeoJsonSource(GeoJsonData.Features(Point(marker.toPosition())))
                CircleLayer(
                    id = "marker",
                    source = source,
                    color = const(markerColor),
                    radius = const(9.dp),
                    strokeColor = const(Color.White),
                    strokeWidth = const(3.dp),
                )
            }
            RouteLabels(routesState.value, routeColors) { id -> controllerRef[0]?.onRouteClick?.invoke(id) }
            measuringState.value?.let { points ->
                MeasureLayers(points + listOfNotNull(controllerRef[0]?.center), markerColor)
            }
            puckState.value?.let { puck ->
                val source = rememberGeoJsonSource(GeoJsonData.Features(Point(puck.position.toPosition())))
                if (puck.bearing == null) {
                    CircleLayer(
                        id = "navigation-puck",
                        source = source,
                        color = const(markerColor),
                        radius = const(9.dp),
                        strokeColor = const(Color.White),
                        strokeWidth = const(3.dp),
                    )
                    return@let
                }
                SymbolLayer(
                    id = "navigation-puck",
                    source = source,
                    iconImage = puckIcon,
                    iconRotate = const(puck.bearing.toFloat()),
                    iconRotationAlignment = const(IconRotationAlignment.Map),
                    iconPitchAlignment = const(IconPitchAlignment.Viewport),
                    iconAllowOverlap = const(true),
                    iconIgnorePlacement = const(true),
                )
            }
        }
    LaunchedEffect(mapState) {
        // Read again when the style reloads, as on switching to dark mode, which brings the layer back visible.
        snapshotFlow { mapState.style.layers[BUILDINGS_3D_LAYER] to (MapLayer.BUILDINGS_3D in layersState.value) }
            .collect { (layer, shown) ->
                runCatching { layer?.asMutable?.setLayoutProperty("visibility", JsonPrimitive(if (shown) "visible" else "none")) }
            }
    }
    return remember(mapState, location) {
        MapController(
            mapState,
            location,
            markerState,
            linesState,
            stopsState,
            resultsState,
            resultIconState,
            layersState,
            sharingState,
            routesState,
            roadEventsState,
            puckState,
            parkedCarState,
            measuringState,
            recordingState,
            radarState,
            radarFrameState,
            cloudFrameState,
            vehiclesState,
            aircraftState,
        )
    }
        .also { controllerRef[0] = it }
}

internal fun Position.toLatLng() = LatLng(latitude = latitude, longitude = longitude)

internal fun LatLng.toPosition() = Position(longitude = longitude, latitude = latitude)

internal const val LIGHT_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val BUILDINGS_3D_LAYER = "building-3d"
private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"
private val StyleRetryFirst = 5.seconds
private val StyleRetryMax = 2.minutes

/** The darkened [loadLight] style, with the plain dark style in its place while loading fails and is retried. */
internal fun darkStyles(loadLight: suspend () -> String): Flow<BaseStyle> =
    flow {
        var pause = StyleRetryFirst
        var fallback = false
        while (true) {
            val light =
                try {
                    loadLight()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            if (light != null) {
                emit(BaseStyle.Json(darkStyle(light)))
                return@flow
            }
            if (!fallback) emit(BaseStyle.Uri(DARK_STYLE))
            fallback = true
            delay(pause)
            pause = minOf(pause * 2, StyleRetryMax)
        }
    }

internal val LoadingDarkStyle =
    BaseStyle.Json(
        """{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#1c1c1c"}}]}""",
    )
private const val USER_ZOOM = 15.0
private const val ENGAGE_TIMEOUT_MILLIS = 5_000L
private const val ENGAGE_POLL_MILLIS = 50L

/** Time for the tiles of a new view to load before places are read from them. */
private const val TILE_SETTLE_MILLIS = 600L
private const val PLACE_ZOOM = 16.0
private const val STOP_INDEX = "i"
private const val STOP_ICON = "m"
private val StopIconTint = Color(0xFF303030)

/** Below this zoom there are too many stops in view to load or draw. */
const val STOPS_MIN_ZOOM = 14.0
private val FIT_MARGIN = 48.dp
internal val FollowAnimation = 900.milliseconds
private const val ROUTE_EVENT_DISTANCE = 30.0
private val EUROPE = Position(longitude = 10.0, latitude = 50.0)

/** The icon drawn inside a stop dot, chosen by the most significant mode serving the stop. */
private enum class StopIcon(
    val vector: ImageVector,
    val modes: Set<TransportMode>,
) {
    RAIL(
        Icons.Default.Train,
        setOf(
            TransportMode.HIGHSPEED_RAIL,
            TransportMode.LONG_DISTANCE_RAIL,
            TransportMode.NIGHT_RAIL,
            TransportMode.REGIONAL_RAIL,
            TransportMode.SUBURBAN,
        ),
    ),
    SUBWAY(Icons.Default.DirectionsSubway, setOf(TransportMode.SUBWAY)),
    TRAM(Icons.Default.Tram, setOf(TransportMode.TRAM, TransportMode.CABLE_CAR, TransportMode.FUNICULAR)),
    FERRY(Icons.Default.DirectionsBoat, setOf(TransportMode.FERRY)),
    BUS(Icons.Default.DirectionsBus, emptySet()),
    ;

    companion object {
        fun of(modes: Set<TransportMode>): StopIcon = entries.firstOrNull { icon -> icon.modes.any { it in modes } } ?: BUS
    }
}
