package nz.eloque.openmaps.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import nz.eloque.openmaps.core.model.LatLng
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToString
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.textOffset
import org.maplibre.compose.expressions.value.IconPitchAlignment
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.FeaturesClickHandler
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point

/** An aircraft where it is right now, turned to its track and labelled with its callsign. */
data class AircraftMarker(
    val id: String,
    val label: String?,
    val location: LatLng,
    val track: Double?,
    val onGround: Boolean,
    val selected: Boolean = false,
)

@Composable
internal fun AircraftLayers(
    aircraft: List<AircraftMarker>,
    dark: Boolean,
    selectedColor: Color,
    onClick: (AircraftMarker) -> Unit,
) {
    if (aircraft.isEmpty()) return
    val features =
        aircraft.mapIndexed { index, plane ->
            Feature(
                Point(plane.location.toPosition()),
                buildJsonObject {
                    put(INDEX, index)
                    put(TRACK, plane.track ?: 0.0)
                    put(
                        STATE,
                        if (plane.selected) {
                            "selected"
                        } else if (plane.onGround) {
                            "ground"
                        } else {
                            "air"
                        },
                    )
                    put(LABEL, plane.label.orEmpty())
                },
            )
        }
    val source = rememberGeoJsonSource(GeoJsonData.Features(FeatureCollection(features)))
    val outline = if (dark) PinRingDark else Color.White
    val painter = rememberVectorPainter(Icons.Default.Flight)
    val airColor = if (dark) AirDark else AirLight
    val air = image(remember(painter, airColor, outline) { BadgePainter(painter, airColor, outline) }, size = DpSize(26.dp, 26.dp))
    val ground = image(remember(painter, outline) { BadgePainter(painter, GroundColor, outline) }, size = DpSize(20.dp, 20.dp))
    val selected =
        image(remember(painter, selectedColor, outline) { BadgePainter(painter, selectedColor, outline) }, size = DpSize(32.dp, 32.dp))
    val handler: FeaturesClickHandler = { clicked ->
        val plane = clicked.firstOrNull()?.properties?.get(INDEX)?.jsonPrimitive?.intOrNull?.let(aircraft::getOrNull)
        if (plane == null) {
            ClickResult.Pass
        } else {
            onClick(plane)
            ClickResult.Consume
        }
    }
    SymbolLayer(
        id = "aircraft",
        source = source,
        iconImage =
            switch(
                feature[STATE].convertToString(),
                case("selected", selected),
                case("ground", ground),
                fallback = air,
            ),
        iconRotate = feature[TRACK].asNumber(),
        iconRotationAlignment = const(IconRotationAlignment.Map),
        iconPitchAlignment = const(IconPitchAlignment.Viewport),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        onClick = handler,
    )
    SymbolLayer(
        id = "aircraft-labels",
        source = source,
        minZoom = LABEL_MIN_ZOOM,
        textField = feature[LABEL].asString().cast(),
        textFont = const(listOf("Noto Sans Regular")),
        textSize = const(0.75f.em),
        textColor = const(if (dark) Color.White else Color(0xFF202124)),
        textHaloColor = const(if (dark) Color(0xFF121212) else Color.White),
        textHaloWidth = const(1.5.dp),
        textAnchor = const(SymbolAnchor.Left),
        textOffset = textOffset(1.4f.em, 0f.em),
        textOptional = const(true),
        onClick = handler,
    )
}

private val AirLight = Color(0xFF1A73E8)
private val AirDark = Color(0xFF8AB4F8)
private val GroundColor = Color(0xFF80868B)

private const val LABEL_MIN_ZOOM = 9f
private const val INDEX = "i"
private const val TRACK = "r"
private const val STATE = "s"
private const val LABEL = "l"
