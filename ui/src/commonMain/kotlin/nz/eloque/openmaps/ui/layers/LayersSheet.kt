package nz.eloque.openmaps.ui.layers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.MapLayer
import nz.eloque.openmaps.core.model.RadarColourScheme
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.close
import nz.eloque.openmaps.ui.resources.layer_buildings
import nz.eloque.openmaps.ui.resources.layer_clouds
import nz.eloque.openmaps.ui.resources.layer_cycling
import nz.eloque.openmaps.ui.resources.layer_default
import nz.eloque.openmaps.ui.resources.layer_flights
import nz.eloque.openmaps.ui.resources.layer_hiking
import nz.eloque.openmaps.ui.resources.layer_options_hint
import nz.eloque.openmaps.ui.resources.layer_rain_radar
import nz.eloque.openmaps.ui.resources.layer_roadworks
import nz.eloque.openmaps.ui.resources.layer_seamarks
import nz.eloque.openmaps.ui.resources.layer_terrain
import nz.eloque.openmaps.ui.resources.layer_transit
import nz.eloque.openmaps.ui.resources.layer_vehicles
import nz.eloque.openmaps.ui.resources.map_details
import nz.eloque.openmaps.ui.resources.map_type
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val Details =
    listOf(
        MapLayer.TRANSIT,
        MapLayer.VEHICLES,
        MapLayer.FLIGHTS,
        MapLayer.CYCLING,
        MapLayer.HIKING,
        MapLayer.ROADWORKS,
        MapLayer.BUILDINGS_3D,
        MapLayer.SEAMARKS,
        MapLayer.RAIN_RADAR,
        MapLayer.CLOUDS,
    )

/** Chooses the map type and switches the optional map details on and off. [colourSchemes] are empty until the radar has loaded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersSheet(
    colourSchemes: List<RadarColourScheme>,
    onDismiss: () -> Unit,
) {
    val settings = koinInject<SettingsStore>()
    val layers by settings.mapLayers.collectAsStateWithLifecycle()
    var optionsOf by rememberSaveable { mutableStateOf<MapLayer?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
    // Back is handled here alone, since sharing it with the sheet's own handling loses a press after the options close.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        NavigationBackHandler(
            rememberNavigationEventState(NavigationEventInfo.None),
            onBackCompleted = { if (optionsOf != null) optionsOf = null else close() },
        )
        optionsOf?.let { layer ->
            LayerOptions(layer, colourSchemes, onBack = { optionsOf = null }, onDismiss = close)
            return@ModalBottomSheet
        }
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                Text(stringResource(Res.string.map_type), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close)) }
            }
            TileGrid(
                listOf(
                    {
                        LayerTile(Res.string.layer_default, MapLayer.TERRAIN !in layers, {
                            settings.setMapLayers(layers - MapLayer.TERRAIN)
                        }) {
                            drawDefault()
                        }
                    },
                    {
                        LayerTile(Res.string.layer_terrain, MapLayer.TERRAIN in layers, {
                            settings.setMapLayers(layers + MapLayer.TERRAIN)
                        }) {
                            drawTerrain()
                        }
                    },
                ),
                Modifier.padding(top = 16.dp, bottom = 24.dp),
            )
            HorizontalDivider(Modifier.padding(horizontal = 12.dp))
            Text(
                stringResource(Res.string.map_details),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 12.dp, top = 24.dp),
            )
            Text(
                stringResource(Res.string.layer_options_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 16.dp),
            )
            TileGrid(
                Details.map { layer ->
                    {
                        val on = layer in layers
                        LayerTile(
                            layer.label,
                            on,
                            { settings.setMapLayers(if (on) layers - layer else layers + layer) },
                            layer.badge,
                            onLongClick = { optionsOf = layer }.takeIf { layer.hasOptions },
                        ) {
                            layer.drawing(this)
                        }
                    }
                },
            )
        }
    }
}

/** [tiles] in rows of equal columns across the sheet, so every section lines up. */
@Composable
private fun TileGrid(
    tiles: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        tiles.chunked(COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { tile -> Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { tile() } }
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A picture of a map layer with its name, outlined in the theme colour while [selected]. */
@Composable
private fun LayerTile(
    label: StringResource,
    selected: Boolean,
    onClick: () -> Unit,
    badge: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
    drawing: DrawScope.() -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(TileWidth).combinedClickable(role = Role.Checkbox, onLongClick = onLongClick, onClick = onClick),
    ) {
        Box(
            Modifier
                .size(TileSize)
                .border(if (selected) BorderStroke(3.dp, accent) else BorderStroke(0.dp, Color.Transparent), shape)
                .padding(if (selected) 5.dp else 0.dp)
                .clip(RoundedCornerShape(if (selected) 10.dp else 14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize(), onDraw = drawing)
            badge?.let {
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(BadgeBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

internal val MapLayer.label: StringResource
    get() =
        when (this) {
            MapLayer.TERRAIN -> Res.string.layer_terrain
            MapLayer.TRANSIT -> Res.string.layer_transit
            MapLayer.CYCLING -> Res.string.layer_cycling
            MapLayer.HIKING -> Res.string.layer_hiking
            MapLayer.ROADWORKS -> Res.string.layer_roadworks
            MapLayer.BUILDINGS_3D -> Res.string.layer_buildings
            MapLayer.SEAMARKS -> Res.string.layer_seamarks
            MapLayer.RAIN_RADAR -> Res.string.layer_rain_radar
            MapLayer.CLOUDS -> Res.string.layer_clouds
            MapLayer.VEHICLES -> Res.string.layer_vehicles
            MapLayer.FLIGHTS -> Res.string.layer_flights
        }

private val MapLayer.badge: ImageVector?
    get() =
        when (this) {
            MapLayer.TRANSIT -> Icons.Default.DirectionsTransit
            MapLayer.CYCLING -> Icons.AutoMirrored.Filled.DirectionsBike
            MapLayer.HIKING -> Icons.Default.Hiking
            MapLayer.ROADWORKS -> Icons.Default.Construction
            MapLayer.SEAMARKS -> Icons.Default.Anchor
            MapLayer.RAIN_RADAR -> Icons.Default.Umbrella
            MapLayer.CLOUDS -> Icons.Default.Cloud
            MapLayer.VEHICLES -> Icons.Default.DirectionsBus
            MapLayer.FLIGHTS -> Icons.Default.Flight
            else -> null
        }

private val MapLayer.drawing: DrawScope.() -> Unit
    get() =
        when (this) {
            MapLayer.TRANSIT -> {
                { drawTransit() }
            }
            MapLayer.CYCLING -> {
                { drawCycling() }
            }
            MapLayer.HIKING -> {
                { drawHiking() }
            }
            MapLayer.ROADWORKS -> {
                { drawRoadworks() }
            }
            MapLayer.BUILDINGS_3D -> {
                { drawBuildings() }
            }
            MapLayer.TERRAIN -> {
                { drawTerrain() }
            }
            MapLayer.SEAMARKS -> {
                { drawSeamarks() }
            }
            MapLayer.RAIN_RADAR -> {
                { drawRadar() }
            }
            MapLayer.VEHICLES -> {
                { drawVehicles() }
            }
            MapLayer.FLIGHTS -> {
                { drawFlights() }
            }
            MapLayer.CLOUDS -> {
                { drawClouds() }
            }
        }

private fun DrawScope.land() {
    drawRect(Land)
    drawPath(
        Path().apply {
            moveTo(0f, size.height * 0.62f)
            cubicTo(size.width * 0.3f, size.height * 0.5f, size.width * 0.25f, size.height * 0.85f, size.width * 0.55f, size.height)
            lineTo(0f, size.height)
            close()
        },
        Water,
    )
    drawPath(
        Path().apply {
            moveTo(size.width * 0.45f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height * 0.4f)
            cubicTo(size.width * 0.8f, size.height * 0.45f, size.width * 0.55f, size.height * 0.3f, size.width * 0.45f, 0f)
            close()
        },
        Park,
    )
}

private fun DrawScope.road(
    color: Color = Color.White,
    outline: Color = RoadOutline,
) {
    val path =
        Path().apply {
            moveTo(size.width * 0.25f, size.height)
            cubicTo(size.width * 0.35f, size.height * 0.5f, size.width * 0.75f, size.height * 0.55f, size.width, size.height * 0.15f)
        }
    drawPath(path, outline, style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, color, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawDefault() {
    land()
    road()
}

private fun DrawScope.drawTerrain() {
    drawRect(TerrainLow)
    drawPath(
        Path().apply {
            moveTo(0f, size.height * 0.7f)
            lineTo(size.width * 0.35f, size.height * 0.2f)
            lineTo(size.width * 0.6f, size.height * 0.55f)
            lineTo(size.width * 0.8f, size.height * 0.3f)
            lineTo(size.width, size.height * 0.6f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        },
        TerrainHigh,
    )
    drawPath(
        Path().apply {
            moveTo(size.width * 0.35f, size.height * 0.2f)
            lineTo(size.width * 0.5f, size.height * 0.42f)
            lineTo(size.width * 0.3f, size.height * 0.7f)
            close()
        },
        TerrainShadow,
    )
    road()
}

private fun DrawScope.drawTransit() {
    drawRect(Land)
    listOf(0.3f to Rail, 0.45f to Subway, 0.72f to Tram).forEach { (y, color) ->
        drawLine(color, Offset(0f, size.height * y), Offset(size.width, size.height * (y - 0.08f)), 4.dp.toPx())
    }
}

private fun DrawScope.drawCycling() {
    land()
    drawCircle(CycleGreen, radius = size.minDimension * 0.3f, style = Stroke(width = 5.dp.toPx()))
    drawLine(CycleGreen, Offset(size.width * 0.5f, size.height * 0.8f), Offset(size.width * 0.45f, size.height), 5.dp.toPx())
}

private fun DrawScope.drawHiking() {
    drawRect(Park)
    val path =
        Path().apply {
            moveTo(0f, size.height * 0.8f)
            cubicTo(size.width * 0.3f, size.height * 0.2f, size.width * 0.6f, size.height * 0.95f, size.width, size.height * 0.25f)
        }
    drawPath(path, HikeRed, style = Stroke(width = 4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f))))
}

private fun DrawScope.drawRoadworks() {
    land()
    road()
    drawLine(
        Works,
        Offset(size.width * 0.62f, size.height * 0.44f),
        Offset(size.width * 0.9f, size.height * 0.24f),
        5.dp.toPx(),
        StrokeCap.Round,
    )
}

private fun DrawScope.drawClouds() {
    land()
    listOf(Triple(0.3f, 0.35f, 0.26f), Triple(0.62f, 0.3f, 0.3f), Triple(0.75f, 0.7f, 0.22f)).forEach { (x, y, r) ->
        drawCircle(CloudGrey, radius = size.minDimension * r, center = Offset(size.width * x, size.height * y + 2.dp.toPx()))
        drawCircle(CloudWhite, radius = size.minDimension * r, center = Offset(size.width * x, size.height * y))
    }
}

private fun DrawScope.drawRadar() {
    land()
    drawCircle(RainLight, radius = size.minDimension * 0.34f, center = Offset(size.width * 0.42f, size.height * 0.45f))
    drawCircle(RainLight, radius = size.minDimension * 0.22f, center = Offset(size.width * 0.72f, size.height * 0.62f))
    drawCircle(RainHeavy, radius = size.minDimension * 0.16f, center = Offset(size.width * 0.46f, size.height * 0.42f))
}

private fun DrawScope.drawSeamarks() {
    drawRect(Water)
    drawLine(
        Fairway,
        Offset(size.width * 0.1f, size.height),
        Offset(size.width * 0.9f, 0f),
        2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f)),
    )
    listOf(0.25f to 0.45f, 0.55f to 0.15f).forEach { (x, y) ->
        drawCircle(BuoyRed, radius = 5.dp.toPx(), center = Offset(size.width * x, size.height * y))
    }
    listOf(0.48f to 0.85f, 0.8f to 0.55f).forEach { (x, y) ->
        drawCircle(BuoyGreen, radius = 5.dp.toPx(), center = Offset(size.width * x, size.height * y))
    }
}

private fun DrawScope.drawBuildings() {
    drawRect(BuildingGround)

    fun box(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
    ) {
        val d = w * 0.35f
        drawPath(
            Path().apply {
                moveTo(x, y)
                lineTo(x + w, y)
                lineTo(x + w, y + h)
                lineTo(x, y + h)
                close()
            },
            BuildingFront,
        )
        drawPath(
            Path().apply {
                moveTo(x, y)
                lineTo(x + d, y - d)
                lineTo(x + w + d, y - d)
                lineTo(x + w, y)
                close()
            },
            BuildingTop,
        )
        drawPath(
            Path().apply {
                moveTo(x + w, y)
                lineTo(x + w + d, y - d)
                lineTo(x + w + d, y + h - d)
                lineTo(x + w, y + h)
                close()
            },
            BuildingSide,
        )
    }
    box(size.width * 0.18f, size.height * 0.35f, size.width * 0.3f, size.height * 0.4f)
    box(size.width * 0.5f, size.height * 0.5f, size.width * 0.28f, size.height * 0.3f)
}

private val TileSize = 72.dp
private val TileWidth = 80.dp
private const val COLUMNS = 4

private val Land = Color(0xFFF1F3F4)
private val Water = Color(0xFFAADAFF)
private val Park = Color(0xFFCDEBD0)
private val RoadOutline = Color(0xFFC4C7C5)
private val TerrainLow = Color(0xFFE3E8DC)
private val TerrainHigh = Color(0xFFBCC8AE)
private val TerrainShadow = Color(0xFF9EAD8F)
private val Rail = Color(0xFF1565C0)
private val Subway = Color(0xFF6A1B9A)
private val Tram = Color(0xFFC62828)
private val CycleGreen = Color(0xFF188038)
private val HikeRed = Color(0xFFD93025)
private val Works = Color(0xFFE8710A)
private val BadgeBlue = Color(0xFF1A73E8)
private val BuildingGround = Color(0xFFDDF2E4)
private val BuildingFront = Color(0xFFE8EAED)
private val BuildingTop = Color(0xFFFFFFFF)
private val BuildingSide = Color(0xFFBDC1C6)
private val BuoyRed = Color(0xFFD93025)
private val BuoyGreen = Color(0xFF188038)
private val Fairway = Color(0xFF7A3E9D)
private val RainLight = Color(0xFF8AB4F8)
private val RainHeavy = Color(0xFF1A73E8)
private val CloudWhite = Color(0xFFF8F9FA)
private val CloudGrey = Color(0xFFDADCE0)

private fun DrawScope.drawVehicles() {
    land()
    val track =
        Path().apply {
            moveTo(0f, size.height * 0.75f)
            cubicTo(size.width * 0.35f, size.height * 0.7f, size.width * 0.55f, size.height * 0.25f, size.width, size.height * 0.2f)
        }
    drawPath(track, VehicleTrack, style = Stroke(3.dp.toPx()))
    listOf(Triple(0.22f, 0.72f, VehicleRed), Triple(0.66f, 0.3f, VehiclePurple)).forEach { (x, y, color) ->
        drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(size.width * x, size.height * y))
        drawCircle(color, radius = 6.5.dp.toPx(), center = Offset(size.width * x, size.height * y))
    }
}

private val VehicleTrack = Color(0xFFB0B7C3)
private val VehicleRed = Color(0xFFD32F2F)
private val VehiclePurple = Color(0xFF7B1FA2)

private fun DrawScope.drawFlights() {
    land()
    val route =
        Path().apply {
            moveTo(size.width * 0.1f, size.height * 0.85f)
            quadraticTo(size.width * 0.45f, size.height * 0.05f, size.width * 0.95f, size.height * 0.3f)
        }
    drawPath(
        route,
        VehicleTrack,
        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
    listOf(Offset(size.width * 0.3f, size.height * 0.45f), Offset(size.width * 0.72f, size.height * 0.24f)).forEach { center ->
        drawCircle(Color.White, radius = 8.dp.toPx(), center = center)
        drawCircle(FlightBlue, radius = 6.5.dp.toPx(), center = center)
    }
}

private val FlightBlue = Color(0xFF1A73E8)
