package nz.eloque.openmaps.ui.flights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import nz.eloque.openmaps.core.model.AirTraffic
import nz.eloque.openmaps.core.model.Aircraft
import nz.eloque.openmaps.core.model.Airport
import nz.eloque.openmaps.core.model.DistanceUnit
import nz.eloque.openmaps.core.model.FlightInfo
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.greatCircleTo
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.map.MapLine
import nz.eloque.openmaps.ui.components.FlightTracker
import nz.eloque.openmaps.ui.components.backoff
import nz.eloque.openmaps.ui.components.formatHeight
import nz.eloque.openmaps.ui.components.formatSpeed
import nz.eloque.openmaps.ui.navigation.AircraftKey
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.aircraft_altitude
import nz.eloque.openmaps.ui.resources.aircraft_climbing
import nz.eloque.openmaps.ui.resources.aircraft_descending
import nz.eloque.openmaps.ui.resources.aircraft_lost
import nz.eloque.openmaps.ui.resources.aircraft_model
import nz.eloque.openmaps.ui.resources.aircraft_on_ground
import nz.eloque.openmaps.ui.resources.aircraft_route
import nz.eloque.openmaps.ui.resources.aircraft_source
import nz.eloque.openmaps.ui.resources.aircraft_speed
import nz.eloque.openmaps.ui.resources.aircraft_usual_route
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** One aircraft with its flight, kept current while open. */
@Composable
fun AircraftSheet(
    key: AircraftKey,
    map: MapController,
) {
    val traffic = koinInject<AirTraffic>()
    val tracker = koinInject<FlightTracker>()
    val unit by koinInject<SettingsStore>().distanceUnit.collectAsState()
    val known by tracker.aircraft.collectAsState()
    val aircraft = known[key.id]
    DisposableEffect(key.id) {
        tracker.selected.value = key.id
        onDispose { if (tracker.selected.value == key.id) tracker.selected.value = null }
    }
    LaunchedEffect(key.id) {
        while (true) {
            val latest = tracker.aircraft.value[key.id]
            var wait = CHECK
            if (latest == null || Clock.System.now() - latest.positionTime > STALE) {
                try {
                    traffic.aircraft(key.id)?.let(tracker::put)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    wait = e.backoff()
                }
            }
            delay(wait)
        }
    }
    LaunchedEffect(key.id) {
        val first = tracker.aircraft.first { key.id in it }.getValue(key.id)
        map.show(first.positionAt(Clock.System.now()), zoomIn = false)
    }
    val callsign = aircraft?.callsign
    val info by produceState<FlightInfo?>(null, key.id, callsign) {
        val plane = aircraft ?: return@produceState
        value =
            try {
                traffic.info(plane)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
    }
    ShowFlightOnMap(map, aircraft, info)

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Flight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp).rotate(aircraft?.trackDegrees?.toFloat() ?: 0f),
                )
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(
                    callsign ?: aircraft?.registration ?: key.label ?: key.id.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                )
                info?.airline?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            }
        }
        if (aircraft == null) {
            Text(stringResource(Res.string.aircraft_lost), style = MaterialTheme.typography.bodyLarge)
        } else {
            info?.let { flight -> Route(flight.origin, flight.destination) }
            Details(aircraft, info, unit)
        }
        Text(
            stringResource(Res.string.aircraft_source),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun Route(
    origin: Airport?,
    destination: Airport?,
) {
    if (origin == null || destination == null) return
    Column {
        Text(
            stringResource(Res.string.aircraft_route, origin.label, destination.label),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(Res.string.aircraft_usual_route),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Details(
    aircraft: Aircraft,
    info: FlightInfo?,
    unit: DistanceUnit,
) {
    val model = listOfNotNull(info?.model ?: aircraft.typeCode, aircraft.registration).joinToString(" · ")
    if (model.isNotEmpty()) Text(stringResource(Res.string.aircraft_model, model), style = MaterialTheme.typography.bodyLarge)
    val altitude = aircraft.altitudeMeters
    if (altitude == null) {
        Text(stringResource(Res.string.aircraft_on_ground), style = MaterialTheme.typography.bodyLarge)
    } else {
        val rate = aircraft.verticalRateMetersPerSecond ?: 0.0
        val trend =
            when {
                rate > LEVEL_RATE -> stringResource(Res.string.aircraft_climbing)
                rate < -LEVEL_RATE -> stringResource(Res.string.aircraft_descending)
                else -> null
            }
        Text(
            listOfNotNull(stringResource(Res.string.aircraft_altitude, formatHeight(altitude, unit)), trend).joinToString(", "),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    aircraft.groundSpeedMetersPerSecond?.takeIf { altitude != null }?.let {
        Text(stringResource(Res.string.aircraft_speed, formatSpeed(it, unit)), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Draws the usual route through the aircraft's position while composed: the part flown faintly, the rest strongly. */
@Composable
private fun ShowFlightOnMap(
    map: MapController,
    aircraft: Aircraft?,
    info: FlightInfo?,
) {
    val ahead = MaterialTheme.colorScheme.primary
    val behind = MaterialTheme.colorScheme.outline
    val origin = info?.origin ?: return
    val destination = info.destination ?: return
    val drawn = remember { arrayOfNulls<List<MapLine>>(1) }
    LaunchedEffect(aircraft, origin, destination) {
        while (aircraft != null) {
            val here = aircraft.positionAt(Clock.System.now())
            val lines =
                listOf(
                    MapLine(origin.location.greatCircleTo(here), behind, 3.dp),
                    MapLine(here.greatCircleTo(destination.location), ahead, 4.dp),
                )
            map.lines = lines
            drawn[0] = lines
            delay(LINE_TICK)
        }
    }
    DisposableEffect(map) { onDispose { if (map.lines === drawn[0]) map.lines = emptyList() } }
}

private val Airport.label: String
    get() = listOfNotNull(iata ?: icao, city ?: name).joinToString(" ")

private const val LEVEL_RATE = 1.0
private val CHECK = 15.seconds
private val STALE = 45.seconds
private val LINE_TICK = 5.seconds

/** Credits for the map attribution while the flights layer is on. */
const val FLIGHTS_ATTRIBUTION =
    "Aircraft by <a href=\"https://www.adsb.lol\">adsb.lol</a> under ODbL 1.0, " +
        "routes and aircraft models from <a href=\"https://github.com/vradarserver/standing-data\">VRS standing data</a>"
