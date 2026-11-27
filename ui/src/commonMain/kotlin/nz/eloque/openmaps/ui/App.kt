package nz.eloque.openmaps.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.savedstate.serialization.SavedStateConfiguration
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import nz.eloque.compose_kit.navigation.slideBackward
import nz.eloque.compose_kit.navigation.slideForward
import nz.eloque.openmaps.core.model.Geocoder
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.MapLayer
import nz.eloque.openmaps.core.model.Navigator
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.PlacesStore
import nz.eloque.openmaps.core.model.RainRadar
import nz.eloque.openmaps.core.model.RoadEvents
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.TrackStore
import nz.eloque.openmaps.core.model.TransportMode
import nz.eloque.openmaps.core.model.TravelMode
import nz.eloque.openmaps.core.model.TripGuide
import nz.eloque.openmaps.core.platform.Files
import nz.eloque.openmaps.map.MapAction
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.map.OpenMap
import nz.eloque.openmaps.map.rememberMapController
import nz.eloque.openmaps.ui.about.AboutPage
import nz.eloque.openmaps.ui.about.DataSourcesPage
import nz.eloque.openmaps.ui.about.PrivacyPage
import nz.eloque.openmaps.ui.category.CategoryChips
import nz.eloque.openmaps.ui.category.CategoryChipsHeight
import nz.eloque.openmaps.ui.category.CategorySheet
import nz.eloque.openmaps.ui.category.label
import nz.eloque.openmaps.ui.components.FlightTracker
import nz.eloque.openmaps.ui.components.FlightsOnMap
import nz.eloque.openmaps.ui.components.StopsOnMap
import nz.eloque.openmaps.ui.components.VehiclesOnMap
import nz.eloque.openmaps.ui.components.clockTime
import nz.eloque.openmaps.ui.directions.DirectionsSheet
import nz.eloque.openmaps.ui.directions.RouteSheet
import nz.eloque.openmaps.ui.directions.TripSheet
import nz.eloque.openmaps.ui.flights.AircraftSheet
import nz.eloque.openmaps.ui.flights.FLIGHTS_ATTRIBUTION
import nz.eloque.openmaps.ui.guidance.ActiveTrip
import nz.eloque.openmaps.ui.guidance.NavigationBanner
import nz.eloque.openmaps.ui.guidance.NavigationSheet
import nz.eloque.openmaps.ui.guidance.SpeedSign
import nz.eloque.openmaps.ui.guidance.TransitGuidanceBanner
import nz.eloque.openmaps.ui.guidance.TransitGuidanceSheet
import nz.eloque.openmaps.ui.home.ActivePills
import nz.eloque.openmaps.ui.layers.LayersSheet
import nz.eloque.openmaps.ui.layers.MapDataSheet
import nz.eloque.openmaps.ui.layers.RadarTimeline
import nz.eloque.openmaps.ui.measure.MeasureSheet
import nz.eloque.openmaps.ui.navigation.AboutKey
import nz.eloque.openmaps.ui.navigation.AircraftKey
import nz.eloque.openmaps.ui.navigation.CategoryKey
import nz.eloque.openmaps.ui.navigation.DataSourcesKey
import nz.eloque.openmaps.ui.navigation.DirectionsField
import nz.eloque.openmaps.ui.navigation.DirectionsKey
import nz.eloque.openmaps.ui.navigation.Endpoint
import nz.eloque.openmaps.ui.navigation.HomeKey
import nz.eloque.openmaps.ui.navigation.ListKey
import nz.eloque.openmaps.ui.navigation.MapLink
import nz.eloque.openmaps.ui.navigation.MeasureKey
import nz.eloque.openmaps.ui.navigation.NavigationKey
import nz.eloque.openmaps.ui.navigation.PageKey
import nz.eloque.openmaps.ui.navigation.PlaceKey
import nz.eloque.openmaps.ui.navigation.PrivacyKey
import nz.eloque.openmaps.ui.navigation.RoadEventKey
import nz.eloque.openmaps.ui.navigation.RouteKey
import nz.eloque.openmaps.ui.navigation.SavedKey
import nz.eloque.openmaps.ui.navigation.SearchKey
import nz.eloque.openmaps.ui.navigation.SettingsKey
import nz.eloque.openmaps.ui.navigation.StopKey
import nz.eloque.openmaps.ui.navigation.TrackKey
import nz.eloque.openmaps.ui.navigation.TransitGuidanceKey
import nz.eloque.openmaps.ui.navigation.TripKey
import nz.eloque.openmaps.ui.navigation.TripsKey
import nz.eloque.openmaps.ui.navigation.parseMapLink
import nz.eloque.openmaps.ui.navigation.toEndpoint
import nz.eloque.openmaps.ui.navigation.toKey
import nz.eloque.openmaps.ui.place.PlaceSheet
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.about
import nz.eloque.openmaps.ui.resources.app_name
import nz.eloque.openmaps.ui.resources.clear
import nz.eloque.openmaps.ui.resources.dropped_pin
import nz.eloque.openmaps.ui.resources.imported_track
import nz.eloque.openmaps.ui.resources.map_layers
import nz.eloque.openmaps.ui.resources.menu
import nz.eloque.openmaps.ui.resources.my_location
import nz.eloque.openmaps.ui.resources.parked_car
import nz.eloque.openmaps.ui.resources.parked_since
import nz.eloque.openmaps.ui.resources.record_track
import nz.eloque.openmaps.ui.resources.saved
import nz.eloque.openmaps.ui.resources.search_hint
import nz.eloque.openmaps.ui.resources.settings
import nz.eloque.openmaps.ui.resources.tab_map
import nz.eloque.openmaps.ui.resources.trips
import nz.eloque.openmaps.ui.resources.waiting_for_location
import nz.eloque.openmaps.ui.roads.RoadEventSheet
import nz.eloque.openmaps.ui.saved.ListSheet
import nz.eloque.openmaps.ui.saved.SavedSheet
import nz.eloque.openmaps.ui.search.COORDINATES_CATEGORY
import nz.eloque.openmaps.ui.search.SearchScreen
import nz.eloque.openmaps.ui.settings.SettingsPage
import nz.eloque.openmaps.ui.sharing.SharingSheet
import nz.eloque.openmaps.ui.stop.StopSheet
import nz.eloque.openmaps.ui.theme.OpenMapsTheme
import nz.eloque.openmaps.ui.tracks.TrackRecorder
import nz.eloque.openmaps.ui.tracks.TrackSheet
import nz.eloque.openmaps.ui.trips.TripsSheet
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.random.Random

private val navConfiguration =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(HomeKey::class)
                    subclass(AboutKey::class)
                    subclass(DataSourcesKey::class)
                    subclass(PrivacyKey::class)
                    subclass(SavedKey::class)
                    subclass(TripsKey::class)
                    subclass(TrackKey::class)
                    subclass(MeasureKey::class)
                    subclass(ListKey::class)
                    subclass(SettingsKey::class)
                    subclass(SearchKey::class)
                    subclass(PlaceKey::class)
                    subclass(StopKey::class)
                    subclass(DirectionsKey::class)
                    subclass(RouteKey::class)
                    subclass(CategoryKey::class)
                    subclass(TripKey::class)
                    subclass(AircraftKey::class)
                    subclass(RoadEventKey::class)
                    subclass(NavigationKey::class)
                    subclass(TransitGuidanceKey::class)
                }
            }
    }

private val SheetPeekHeight = 280.dp
private const val CATEGORY_PEEK_FRACTION = 0.45f

/** Differs per app process, so a sheet position saved by an earlier process is not restored and the sheet starts collapsed. */
private val ProcessStart = Random.nextInt()
private val ControlMargin = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    version: String,
    debug: Boolean = false,
    links: Flow<String> = emptyFlow(),
) {
    val http = koinInject<HttpClient>()
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).components { add(KtorNetworkFetcherFactory(httpClient = { http })) }.build()
    }
    OpenMapsTheme {
        val backStack = rememberNavBackStack(navConfiguration, HomeKey)
        GuidedTripLifecycle(backStack)
        val cacheMegabytes by koinInject<SettingsStore>().mapCacheMegabytes.collectAsStateWithLifecycle()
        val map = rememberMapController(loadStyle = { url -> http.get(url).bodyAsText() }, cacheMegabytes = cacheMegabytes)
        val snackbar = remember { SnackbarHostState() }
        val sheetState =
            rememberBottomSheetScaffoldState(
                bottomSheetState = key(ProcessStart) { rememberStandardBottomSheetState() },
                snackbarHostState = snackbar,
            )
        val places = koinInject<PlacesStore>()
        val scope = rememberCoroutineScope()
        val geocoder = koinInject<Geocoder>()
        val pinName = stringResource(Res.string.dropped_pin)

        fun dropPin(
            location: LatLng,
            label: String? = null,
        ) {
            val pin = PlaceKey(label ?: pinName, location.latitude, location.longitude, null, null, null, pinned = true)
            if ((backStack.lastOrNull() as? PlaceKey)?.pinned == true) backStack.removeLastOrNull()
            backStack.add(pin)
            scope.launch {
                val address = runCatching { geocoder.reverse(location) }.getOrNull() ?: return@launch
                val index = backStack.indexOf(pin)
                if (index >= 0) backStack[index] = pin.copy(name = label ?: address.name, subtitle = address.area)
            }
        }
        DisposableEffect(map) {
            map.onLongPress = { location -> dropPin(location) }
            onDispose { map.onLongPress = null }
        }
        val settings = koinInject<SettingsStore>()
        val parkedCar by settings.parkedCar.collectAsState()
        val carName = stringResource(Res.string.parked_car)
        val parkedText = parkedCar?.let { stringResource(Res.string.parked_since, clockTime(it.since)) }
        LaunchedEffect(parkedCar) { map.parkedCar = parkedCar?.location }
        val recorder = koinInject<TrackRecorder>()
        LaunchedEffect(recorder) { recorder.recording.collect { map.recording = it?.points?.map { point -> point.location }.orEmpty() } }
        DisposableEffect(map, parkedCar) {
            map.onParkedCarClick = {
                parkedCar?.let { car ->
                    backStack.add(PlaceKey(carName, car.location.latitude, car.location.longitude, parkedText, null, null, pinned = true))
                }
            }
            onDispose { map.onParkedCarClick = null }
        }
        val files = koinInject<Files>()
        val trackStore = koinInject<TrackStore>()
        val importedName = stringResource(Res.string.imported_track)
        LaunchedEffect(files) {
            files.opened.collect { text ->
                trackStore.importGpx(text, importedName).firstOrNull()?.let { backStack.add(TrackKey(it)) }
            }
        }
        LaunchedEffect(links) {
            links.collect { link ->
                when (val target = parseMapLink(link)) {
                    is MapLink.Show -> dropPin(target.location, target.label)
                    is MapLink.Find -> backStack.add(SearchKey(query = target.query))
                    is MapLink.Route -> {
                        target.mode?.let(settings::setTravelMode)
                        val to =
                            target.to?.let { Endpoint(pinName, it.latitude, it.longitude) }
                                ?: target.query?.let { query ->
                                    runCatching { geocoder.search(query, map.userLocation) }.getOrNull()?.firstOrNull()?.toEndpoint()
                                }
                        if (to != null) {
                            backStack.add(DirectionsKey(from = null, to = to))
                        } else {
                            target.query?.let { backStack.add(SearchKey(query = it)) }
                        }
                    }
                    null -> Unit
                }
            }
        }
        StopsOnMap(map) { stop ->
            val top = backStack.lastOrNull()
            if (top is StopKey || (top as? PlaceKey)?.pinned == true) backStack.removeLastOrNull()
            backStack.add(stop.toKey())
        }
        DisposableEffect(map) {
            map.onPlaceClick = { place ->
                val top = backStack.lastOrNull()
                if (top is StopKey || top is PlaceKey) backStack.removeLastOrNull()
                backStack.add(place.toKey())
            }
            onDispose { map.onPlaceClick = null }
        }
        val roadEvents = koinInject<RoadEvents>()
        LaunchedEffect(map) { roadEvents.tiles.collect { map.roadEvents = it } }
        LaunchedEffect(map) { settings.mapLayers.collect { map.layers = it } }
        val rainRadar = koinInject<RainRadar>()
        val mapLayers by settings.mapLayers.collectAsStateWithLifecycle()
        VehiclesOnMap(map, MapLayer.VEHICLES in mapLayers) { tripId, _ -> backStack.add(TripKey(tripId, null)) }
        FlightsOnMap(map, MapLayer.FLIGHTS in mapLayers) { id -> backStack.add(AircraftKey(id, null)) }
        val radarOn = MapLayer.RAIN_RADAR in mapLayers
        val weatherOn = radarOn || MapLayer.CLOUDS in mapLayers
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(map, weatherOn) {
            if (!weatherOn) {
                map.radar = null
                return@LaunchedEffect
            }
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                rainRadar.frames.collect { frames ->
                    map.radar = frames
                    if (frames != null) {
                        map.radarFrame = frames.latestObserved
                        map.cloudFrame = frames.clouds.lastIndex.coerceAtLeast(0)
                    }
                }
            }
        }
        var showLayers by rememberSaveable { mutableStateOf(false) }
        val layersLabel = stringResource(Res.string.map_layers)
        DisposableEffect(map, layersLabel) {
            map.layersAction = MapAction(Icons.Default.Layers, layersLabel) { showLayers = true }
            onDispose { map.layersAction = null }
        }
        if (showLayers) LayersSheet(colourSchemes = map.radar?.colourSchemes.orEmpty(), onDismiss = { showLayers = false })
        var attributions by remember { mutableStateOf<List<String>?>(null) }
        attributions?.let { MapDataSheet(it, onDismiss = { attributions = null }) }
        val flightsShown by rememberUpdatedState(MapLayer.FLIGHTS in mapLayers)
        DisposableEffect(map) {
            map.onAttributionClick = { attributions = if (flightsShown) it + FLIGHTS_ATTRIBUTION else it }
            onDispose { map.onAttributionClick = null }
        }
        DisposableEffect(map) {
            map.onRoadEventClick = { properties ->
                val top = backStack.lastOrNull()
                if (top is StopKey || top is PlaceKey || top is RoadEventKey) backStack.removeLastOrNull()
                backStack.add(RoadEventKey(properties))
            }
            onDispose { map.onRoadEventClick = null }
        }
        LaunchedEffect(map) {
            snapshotFlow { backStack.any { it is CategoryKey || it is ListKey } }.collect {
                if (!it) {
                    map.results = emptyList()
                    map.resultIcon = null
                }
            }
        }

        // The map and sheet always show the screen below any pages, so a page closing reveals them unchanged.
        val top = backStack.lastOrNull { it !is PageKey }
        val pageOpen = backStack.lastOrNull() is PageKey
        LaunchedEffect(top) {
            if (sheetState.bottomSheetState.currentValue == SheetValue.Expanded) sheetState.bottomSheetState.partialExpand()
        }
        val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        val tab = top?.takeIf { it == HomeKey || it == TripsKey || it == SavedKey }
        val peekHeight =
            when {
                top == HomeKey -> 0.dp
                top is CategoryKey || top == TripsKey || top == SavedKey -> windowHeight * CATEGORY_PEEK_FRACTION
                top is NavigationKey || top is TransitGuidanceKey -> NavigationPeekHeight
                else -> SheetPeekHeight
            } +
                if (tab != null) 0.dp else with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

        fun openTab(key: NavKey) {
            while (backStack.size > 1) backStack.removeLastOrNull()
            if (key != HomeKey) backStack.add(key)
        }
        val recording by recorder.recording.collectAsState()
        val menu =
            listOfNotNull(
                MenuEntry(Icons.Default.FiberManualRecord, stringResource(Res.string.record_track)) {
                    if (map.isLocationPermissionGranted) recorder.start() else map.requestLocationPermission()
                }.takeIf { recording == null },
                MenuEntry(Icons.Outlined.Settings, stringResource(Res.string.settings)) { backStack.add(SettingsKey) },
                MenuEntry(Icons.Outlined.Info, stringResource(Res.string.about)) { backStack.add(AboutKey) },
            )
        val drawer = rememberDrawerState(DrawerValue.Closed)

        ModalNavigationDrawer(
            drawerState = drawer,
            // Swiping would fight with panning the map, so the drawer opens only from the menu button.
            gesturesEnabled = drawer.isOpen,
            drawerContent = {
                ModalDrawerSheet {
                    Text(
                        stringResource(Res.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    )
                    menu.forEach { entry ->
                        NavigationDrawerItem(
                            label = { Text(entry.label) },
                            icon = { Icon(entry.icon, contentDescription = null) },
                            selected = false,
                            onClick = {
                                scope.launch { drawer.close() }
                                entry.onClick()
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    BottomSheetScaffold(
                        modifier = Modifier.weight(1f),
                        scaffoldState = sheetState,
                        sheetPeekHeight = peekHeight,
                        sheetSwipeEnabled = top != HomeKey,
                        sheetContainerColor = Color.Transparent,
                        sheetShadowElevation = 0.dp,
                        sheetDragHandle = null,
                        sheetContent = {
                            val sheetEntries =
                                rememberDecoratedNavEntries(
                                    backStack = backStack.filterNot { it is PageKey },
                                    entryDecorators =
                                        listOf(
                                            rememberSaveableStateHolderNavEntryDecorator(),
                                            rememberViewModelStoreNavEntryDecorator(),
                                        ),
                                    entryProvider =
                                        entryProvider {
                                            entry<HomeKey> {}
                                            entry<TripsKey> {
                                                TripsSheet(
                                                    onDirectionsTo = { backStack.add(DirectionsKey(from = null, to = it.toEndpoint())) },
                                                    onSetFavourite = { backStack.add(SearchKey(saveAs = it)) },
                                                    onOpenRoute = {
                                                        backStack.add(DirectionsKey(it.from?.toEndpoint(), it.to?.toEndpoint()))
                                                    },
                                                    onOpenWatched = { backStack.add(RouteKey(it.id, it.destination)) },
                                                )
                                            }
                                            entry<MeasureKey> { MeasureSheet(it, map) }
                                            entry<TrackKey> { TrackSheet(it, map, onDeleted = { backStack.removeLastOrNull() }) }
                                            entry<SavedKey> {
                                                SavedSheet(
                                                    onOpenPlace = { backStack.add(it.toKey()) },
                                                    onOpenList = { backStack.add(ListKey(it)) },
                                                    onOpenTrack = { backStack.add(TrackKey(it)) },
                                                )
                                            }
                                            entry<ListKey> { key ->
                                                ListSheet(key.listId, map, onOpenPlace = { backStack.add(it.toKey()) })
                                            }
                                            entry<SearchKey> { search ->
                                                val flightTracker = koinInject<FlightTracker>()
                                                SearchScreen(
                                                    near = map.userLocation,
                                                    stopsFirst = search.field != null,
                                                    initialQuery = search.query,
                                                    onFlight =
                                                        if (search.field == null && search.saveAs == null) {
                                                            { aircraft ->
                                                                flightTracker.put(aircraft)
                                                                backStack.removeLastOrNull()
                                                                backStack.add(AircraftKey(aircraft.id, aircraft.callsign))
                                                            }
                                                        } else {
                                                            null
                                                        },
                                                    onDismiss = { backStack.removeLastOrNull() },
                                                    onCategory =
                                                        if (search.field == null && search.saveAs == null) {
                                                            { category ->
                                                                backStack.removeLastOrNull()
                                                                if (backStack.lastOrNull() is CategoryKey) backStack.removeLastOrNull()
                                                                backStack.add(CategoryKey(category))
                                                            }
                                                        } else {
                                                            null
                                                        },
                                                    onSelect = { result ->
                                                        backStack.removeLastOrNull()
                                                        search.saveAs?.let { label ->
                                                            scope.launch { places.saveFavourite(result, label) }
                                                            return@SearchScreen
                                                        }
                                                        val field = search.field
                                                        val index = backStack.indexOfLast { it is DirectionsKey }
                                                        if (field == null &&
                                                            (result as? SearchResult.Place)?.category == COORDINATES_CATEGORY
                                                        ) {
                                                            dropPin(result.location)
                                                        } else if (field == null || index < 0) {
                                                            backStack.add(result.toKey())
                                                        } else {
                                                            val directions = backStack[index] as DirectionsKey
                                                            backStack[index] =
                                                                when (field) {
                                                                    DirectionsField.FROM -> directions.copy(from = result.toEndpoint())
                                                                    DirectionsField.TO -> directions.copy(to = result.toEndpoint())
                                                                    DirectionsField.STOP ->
                                                                        directions.copy(
                                                                            via =
                                                                                directions.via + result.toEndpoint(),
                                                                        )
                                                                }
                                                        }
                                                    },
                                                )
                                            }
                                            entry<PlaceKey> { place ->
                                                PlaceSheet(
                                                    place,
                                                    map,
                                                    onDirections = { backStack.add(DirectionsKey(from = null, to = place.toEndpoint())) },
                                                    onOpenStop = backStack::add,
                                                    onOpenTrip = { tripId, stopName -> backStack.add(TripKey(tripId, stopName)) },
                                                    onMeasure = { backStack.add(MeasureKey(place.latitude, place.longitude)) },
                                                )
                                            }
                                            entry<StopKey> { stop ->
                                                StopSheet(
                                                    stop = stop,
                                                    map = map,
                                                    onDirections = { backStack.add(DirectionsKey(from = null, to = stop.toEndpoint())) },
                                                    onOpenTrip = { tripId, stopName -> backStack.add(TripKey(tripId, stopName)) },
                                                )
                                            }
                                            entry<DirectionsKey> { directions ->
                                                DirectionsSheet(
                                                    directions = directions,
                                                    map = map,
                                                    onEditField = { backStack.add(SearchKey(it)) },
                                                    onSwap = {
                                                        val index = backStack.indexOf(directions)
                                                        if (index >= 0) {
                                                            backStack[index] =
                                                                DirectionsKey(directions.to, directions.from, directions.via.reversed())
                                                        }
                                                    },
                                                    onRemoveStop = { stop ->
                                                        val index = backStack.indexOf(directions)
                                                        if (index >= 0) backStack[index] = directions.copy(via = directions.via - stop)
                                                    },
                                                    onOpenRoute = { backStack.add(RouteKey(it.id, directions.to?.name)) },
                                                    onNavigate = { mode, simulate, routeId ->
                                                        directions.to?.let {
                                                            backStack.add(
                                                                NavigationKey(directions.from, it, mode, simulate, directions.via, routeId),
                                                            )
                                                        }
                                                    },
                                                    canSimulate = debug,
                                                )
                                            }
                                            entry<RouteKey> { route ->
                                                RouteSheet(route, map, snackbar, canSimulate = debug) { simulate ->
                                                    backStack.add(TransitGuidanceKey(route.itineraryId, route.destinationName, simulate))
                                                }
                                            }
                                            entry<CategoryKey> {
                                                if (it.category == PlaceCategory.SHARED_VEHICLES) {
                                                    SharingSheet(map)
                                                    return@entry
                                                }
                                                CategorySheet(
                                                    it,
                                                    map,
                                                    onOpenPlace = { place -> backStack.add(place.toKey()) },
                                                    onDirections = { place ->
                                                        backStack.add(DirectionsKey(from = null, to = place.toEndpoint()))
                                                    },
                                                )
                                            }
                                            entry<TripKey> { TripSheet(it, map) }
                                            entry<AircraftKey> { AircraftSheet(it, map) }
                                            entry<RoadEventKey> { RoadEventSheet(it) }
                                            entry<NavigationKey> { NavigationSheet(it, map, onEnd = { backStack.removeLastOrNull() }) }
                                            entry<TransitGuidanceKey> {
                                                TransitGuidanceSheet(
                                                    it,
                                                    map,
                                                    onEnd = { backStack.removeLastOrNull() },
                                                    onReplan = { destination ->
                                                        backStack.removeLastOrNull()
                                                        backStack.add(DirectionsKey(from = null, to = destination))
                                                    },
                                                )
                                            }
                                        },
                                )
                            SheetDisplay(
                                sheetEntries,
                                sheetState.bottomSheetState,
                                handlesBack = !pageOpen,
                                onBack = { backStack.removeLastOrNull() },
                            )
                        },
                    ) {
                        MapLayer(
                            map = map,
                            peekHeight = peekHeight,
                            showSearchBar = top == HomeKey || top is CategoryKey,
                            banner =
                                when (top) {
                                    is NavigationKey -> { modifier -> NavigationBanner(modifier) }
                                    is TransitGuidanceKey -> { modifier -> TransitGuidanceBanner(modifier) }
                                    else -> null
                                },
                            category = (top as? CategoryKey)?.category,
                            onCategory = { category ->
                                if (top is CategoryKey) backStack.removeLastOrNull()
                                if (category != null) backStack.add(CategoryKey(category))
                            },
                            onSearch = { backStack.add(SearchKey()) },
                            onMenu = { scope.launch { drawer.open() } },
                            pills =
                                if (top == HomeKey) {
                                    { modifier ->
                                        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            val radar = map.radar
                                            if (radar != null && radarOn) {
                                                RadarTimeline(
                                                    radar.frames.map { it.time },
                                                    forecast = { radar.frames[it].forecast },
                                                    selected = map.radarFrame,
                                                    onSelect = { map.radarFrame = it },
                                                )
                                            } else if (radar != null && radar.clouds.size > 1) {
                                                RadarTimeline(
                                                    radar.clouds.map { it.time },
                                                    forecast = { false },
                                                    selected = map.cloudFrame,
                                                    onSelect = { map.cloudFrame = it },
                                                )
                                            }
                                            ActivePills(
                                                userLocation = map.userLocation,
                                                onOpenWatched = { backStack.add(RouteKey(it.id, it.destination)) },
                                                onOpenTrack = { backStack.add(TrackKey(it)) },
                                                onShowCar = { map.onParkedCarClick?.invoke() },
                                                onWalkToCar = {
                                                    parkedCar?.let { car ->
                                                        settings.setTravelMode(TravelMode.WALK)
                                                        backStack.add(
                                                            DirectionsKey(
                                                                from = null,
                                                                to = Endpoint(carName, car.location.latitude, car.location.longitude),
                                                            ),
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                            snackbar = snackbar,
                        )
                    }
                    if (tab != null) TabBar(tab, onSelect = ::openTab)
                }
                NavDisplay(
                    // The map entry fills the screen too, as slides measure their distance from the content they slide to.
                    modifier = Modifier.fillMaxSize(),
                    backStack = listOf<NavKey>(NoPage) + backStack.filterIsInstance<PageKey>(),
                    onBack = { backStack.removeLastOrNull() },
                    transitionSpec = { slideForward() },
                    popTransitionSpec = { slideBackward() },
                    predictivePopTransitionSpec = { slideBackward() },
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                    entryProvider =
                        entryProvider {
                            entry<NoPage> { Box(Modifier.fillMaxSize()) }
                            entry<SettingsKey> { SettingsPage(onBack = { backStack.removeLastOrNull() }) }
                            entry<AboutKey> {
                                AboutPage(
                                    version,
                                    onBack = { backStack.removeLastOrNull() },
                                    onOpenSources = { backStack.add(DataSourcesKey) },
                                    onOpenPrivacy = { backStack.add(PrivacyKey) },
                                )
                            }
                            entry<DataSourcesKey> { DataSourcesPage(onBack = { backStack.removeLastOrNull() }) }
                            entry<PrivacyKey> { PrivacyPage(onBack = { backStack.removeLastOrNull() }) }
                        },
                )
            }
            // Composed after the sheets so it takes back first while the drawer is open.
            NavigationBackHandler(
                rememberNavigationEventState(NavigationEventInfo.None),
                isBackEnabled = drawer.isOpen,
                onBackCompleted = { scope.launch { drawer.close() } },
            )
        }
    }
}

/** Pushes the content down by as much of [statusBar] as the sheet's top edge, at [sheetTop] pixels, has slid under. */
private fun Modifier.belowStatusBar(
    statusBar: Int,
    sheetTop: () -> Float,
) = layout { measurable, constraints ->
    val inset = (statusBar - (runCatching(sheetTop).getOrNull()?.toInt() ?: statusBar)).coerceIn(0, statusBar)
    val placeable = measurable.measure(constraints.offset(vertical = -inset))
    layout(placeable.width, placeable.height + inset) { placeable.place(0, inset) }
}

/**
 * The sheet's surface and its screens. A back gesture sinks the whole sheet down with the finger,
 * and the screen below shows only towards the end of the gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetDisplay(
    entries: List<NavEntry<NavKey>>,
    sheet: SheetState,
    handlesBack: Boolean,
    onBack: () -> Unit,
) {
    val sceneState = rememberSceneState(entries, listOf(SinglePaneSceneStrategy<NavKey>()), onBack = onBack)
    val backState = rememberNavigationEventState(sceneState)
    // Stays registered while disabled, as registering again would put it ahead of the drawer's handler.
    NavigationBackHandler(
        backState,
        isBackEnabled = handlesBack && sceneState.currentScene.previousEntries.isNotEmpty(),
        onBackCompleted = onBack,
    )
    val gesture = (backState.transitionState as? NavigationEventTransitionState.InProgress)?.latestEvent?.progress
    val sink by animateFloatAsState(gesture ?: 0f, if (gesture != null) snap() else spring(), label = "sheet sink")
    val statusBar = WindowInsets.statusBars.getTop(LocalDensity.current)
    Surface(
        shape = BottomSheetDefaults.ExpandedShape,
        color = BottomSheetDefaults.ContainerColor,
        shadowElevation = BottomSheetDefaults.Elevation,
        modifier =
            Modifier.fillMaxHeight().graphicsLayer {
                val visible = size.height - runCatching(sheet::requireOffset).getOrDefault(size.height)
                translationY = sink * visible
            },
    ) {
        // Without full height, content shorter than the peek makes the sheet count as expanded,
        // so it opens fully by itself once results load.
        Column(Modifier.fillMaxHeight().belowStatusBar(statusBar) { sheet.requireOffset() }) {
            BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
            NavDisplay(
                sceneState,
                backState,
                modifier = Modifier.weight(1f),
                transitionSpec = { quickFade() },
                popTransitionSpec = { quickFade() },
                predictivePopTransitionSpec = {
                    fadeIn(tween(SHEET_SWAP_MILLIS, delayMillis = SHEET_SINK_MILLIS)) togetherWith
                        fadeOut(tween(SHEET_SWAP_MILLIS, delayMillis = SHEET_SINK_MILLIS))
                },
            )
        }
    }
}

// The predictive back gesture runs through the whole transition, so the delay keeps the screen until two thirds in.
private const val SHEET_SINK_MILLIS = 200
private const val SHEET_SWAP_MILLIS = 100

/** The bottom of the page stack, showing the map through it. */
private data object NoPage : NavKey

private fun <S> AnimatedContentTransitionScope<S>.quickFade() =
    fadeIn(tween(QUICK_FADE_MILLIS)) togetherWith fadeOut(tween(QUICK_FADE_MILLIS))

private const val QUICK_FADE_MILLIS = 150

@Composable
private fun MapLayer(
    map: MapController,
    peekHeight: Dp,
    showSearchBar: Boolean,
    banner: (@Composable (Modifier) -> Unit)?,
    category: PlaceCategory?,
    onCategory: (PlaceCategory?) -> Unit,
    onSearch: () -> Unit,
    onMenu: () -> Unit,
    pills: (@Composable (Modifier) -> Unit)?,
    snackbar: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val mapPadding =
        PaddingValues(
            start = safe.calculateStartPadding(direction),
            top =
                safe.calculateTopPadding() +
                    when {
                        showSearchBar -> SearchBarHeight + CategoryChipsHeight + ControlMargin * 2
                        banner != null -> NavigationBannerHeight
                        else -> 0.dp
                    },
            end = safe.calculateEndPadding(direction),
            bottom = peekHeight + OrnamentHeight + FabSize + ControlMargin,
        )

    Box(Modifier.fillMaxSize()) {
        val ornamentPadding =
            PaddingValues(
                start = mapPadding.calculateStartPadding(direction),
                end = mapPadding.calculateEndPadding(direction),
                bottom = peekHeight,
            )
        OpenMap(map, Modifier.fillMaxSize(), contentPadding = mapPadding, ornamentPadding = ornamentPadding)

        if (showSearchBar) {
            Column(Modifier.statusBarsPadding().padding(top = ControlMargin)) {
                SearchPill(
                    query = category?.let { stringResource(it.label) },
                    onClick = onSearch,
                    onClear = { onCategory(null) },
                    onMenu = onMenu,
                    modifier = Modifier.padding(horizontal = ControlMargin),
                )
                CategoryChips(
                    selected = category,
                    onSelect = onCategory,
                    contentPadding = PaddingValues(horizontal = ControlMargin),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        if (banner != null) {
            banner(Modifier.statusBarsPadding().padding(ControlMargin))
            CurrentSpeedLimit(Modifier.align(Alignment.BottomStart).padding(start = ControlMargin, bottom = peekHeight + OrnamentHeight))
            return@Box
        }

        pills?.invoke(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = ControlMargin, end = ControlMargin * 2 + FabSize, bottom = peekHeight + OrnamentHeight),
        )

        FloatingActionButton(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = ControlMargin, bottom = peekHeight + OrnamentHeight),
            onClick = {
                if (!map.isLocationPermissionGranted) {
                    map.requestLocationPermission()
                } else {
                    scope.launch {
                        if (!map.centerOnUser()) snackbar.showSnackbar(getString(Res.string.waiting_for_location))
                    }
                }
            },
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = stringResource(Res.string.my_location))
        }
    }
}

private val SearchBarHeight = 56.dp
private val NavigationBannerHeight = 140.dp
private val NavigationPeekHeight = 150.dp
private val FabSize = 56.dp

/** Room for the MapLibre logo and attribution, which sit below the buttons over the map. */
private val OrnamentHeight = 40.dp

@Composable
private fun SearchPill(
    query: String?,
    onClick: () -> Unit,
    onClear: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(Res.string.menu))
            }
            Text(
                query ?: stringResource(Res.string.search_hint),
                color = if (query != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            if (query != null) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.clear))
                }
            }
        }
    }
}

private class MenuEntry(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
private fun TabBar(
    selected: NavKey,
    onSelect: (NavKey) -> Unit,
) {
    NavigationBar {
        listOf(
            Triple(HomeKey, Icons.Outlined.Map, Res.string.tab_map),
            Triple(TripsKey, Icons.Default.Commute, Res.string.trips),
            Triple(SavedKey, Icons.Outlined.Bookmarks, Res.string.saved),
        ).forEach { (key, icon, label) ->
            NavigationBarItem(
                selected = selected == key,
                onClick = { onSelect(key) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

/** Reopens the screen of a trip still running from before the app was closed, and stops the trip once its screen leaves. */
@Composable
private fun GuidedTripLifecycle(backStack: MutableList<NavKey>) {
    val active = koinInject<ActiveTrip>()
    val navigator = koinInject<Navigator>()
    val guide = koinInject<TripGuide>()
    LaunchedEffect(backStack) {
        val running = navigator.state.value != null || guide.state.value != null
        active.key?.takeIf { running && it !in backStack }?.let { backStack.add(it) }
        snapshotFlow { backStack.any { it is NavigationKey || it is TransitGuidanceKey } }.filter { !it }.collect {
            if (active.key != null) {
                navigator.stop()
                guide.stop()
                active.key = null
            }
        }
    }
    LaunchedEffect(backStack) {
        snapshotFlow { active.key }.collect { key ->
            val running = navigator.state.value != null || guide.state.value != null
            if (key == null) {
                backStack.removeAll { it is NavigationKey || it is TransitGuidanceKey }
            } else if (running && key !in backStack) {
                backStack.add(key)
            }
        }
    }
}

@Composable
private fun CurrentSpeedLimit(modifier: Modifier) {
    val state by koinInject<Navigator>().state.collectAsStateWithLifecycle()
    val unit by koinInject<SettingsStore>().distanceUnit.collectAsStateWithLifecycle()
    val trip = state?.takeIf { it.mode == TransportMode.CAR } ?: return
    SpeedSign(trip.speed, trip.speedLimit, trip.speeding, unit, modifier)
}
