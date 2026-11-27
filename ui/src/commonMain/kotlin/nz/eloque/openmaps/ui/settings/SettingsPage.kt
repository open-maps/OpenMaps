package nz.eloque.openmaps.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nz.eloque.compose_kit.settings.SettingsSwitch
import nz.eloque.openmaps.core.model.DistanceUnit
import nz.eloque.openmaps.core.model.ServiceCatalog
import nz.eloque.openmaps.core.model.ServiceRole
import nz.eloque.openmaps.core.model.ServiceSetting
import nz.eloque.openmaps.core.model.ServiceSettingKind
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.map.clearTileCache
import nz.eloque.openmaps.ui.components.Page
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.api_keys
import nz.eloque.openmaps.ui.resources.api_keys_detail
import nz.eloque.openmaps.ui.resources.map_cache_clear
import nz.eloque.openmaps.ui.resources.map_cache_detail
import nz.eloque.openmaps.ui.resources.map_storage
import nz.eloque.openmaps.ui.resources.megabytes
import nz.eloque.openmaps.ui.resources.role_active_routing
import nz.eloque.openmaps.ui.resources.role_charging_stations
import nz.eloque.openmaps.ui.resources.role_flights
import nz.eloque.openmaps.ui.resources.role_fuel_prices
import nz.eloque.openmaps.ui.resources.role_nearby_search
import nz.eloque.openmaps.ui.resources.role_place_details
import nz.eloque.openmaps.ui.resources.role_rain_radar
import nz.eloque.openmaps.ui.resources.role_reviews
import nz.eloque.openmaps.ui.resources.role_road_events
import nz.eloque.openmaps.ui.resources.role_street_routing
import nz.eloque.openmaps.ui.resources.role_transit
import nz.eloque.openmaps.ui.resources.role_weather_forecast
import nz.eloque.openmaps.ui.resources.role_weather_warnings
import nz.eloque.openmaps.ui.resources.server_invalid
import nz.eloque.openmaps.ui.resources.servers
import nz.eloque.openmaps.ui.resources.servers_detail
import nz.eloque.openmaps.ui.resources.service_help
import nz.eloque.openmaps.ui.resources.service_key
import nz.eloque.openmaps.ui.resources.service_server
import nz.eloque.openmaps.ui.resources.settings
import nz.eloque.openmaps.ui.resources.settings_navigation
import nz.eloque.openmaps.ui.resources.settings_routes
import nz.eloque.openmaps.ui.resources.speeding_alert
import nz.eloque.openmaps.ui.resources.speeding_alert_detail
import nz.eloque.openmaps.ui.resources.units
import nz.eloque.openmaps.ui.resources.units_imperial
import nz.eloque.openmaps.ui.resources.units_metric
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun SettingsPage(onBack: () -> Unit) =
    Page(stringResource(Res.string.settings), onBack) {
        val settings = koinInject<SettingsStore>()
        val routeOptions by settings.routeOptions.collectAsStateWithLifecycle()
        val distanceUnit by settings.distanceUnit.collectAsStateWithLifecycle()
        val speedingAlert by settings.speedingAlert.collectAsStateWithLifecycle()
        val cacheMegabytes by settings.mapCacheMegabytes.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val services = koinInject<ServiceCatalog>().settings

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            SectionTitle(Res.string.settings_routes)
            RouteOptionsEditor(routeOptions, settings::setRouteOptions)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle(Res.string.units)
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = distanceUnit == DistanceUnit.METRIC,
                    onClick = { settings.setDistanceUnit(DistanceUnit.METRIC) },
                    label = { Text(stringResource(Res.string.units_metric)) },
                )
                FilterChip(
                    selected = distanceUnit == DistanceUnit.IMPERIAL,
                    onClick = { settings.setDistanceUnit(DistanceUnit.IMPERIAL) },
                    label = { Text(stringResource(Res.string.units_imperial)) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle(Res.string.settings_navigation)
            SettingsSwitch(
                title = stringResource(Res.string.speeding_alert),
                subtitle = stringResource(Res.string.speeding_alert_detail),
                checked = speedingAlert,
                onCheckedChange = settings::setSpeedingAlert,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle(Res.string.map_storage)
            Text(
                stringResource(Res.string.map_cache_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CacheSizes.forEach { megabytes ->
                    FilterChip(
                        selected = cacheMegabytes == megabytes,
                        onClick = { settings.setMapCacheMegabytes(megabytes) },
                        label = { Text(stringResource(Res.string.megabytes, megabytes)) },
                    )
                }
            }
            TextButton(
                onClick = { scope.launch { clearTileCache() } },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text(stringResource(Res.string.map_cache_clear)) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle(Res.string.servers)
            Text(
                stringResource(Res.string.servers_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            services.filter { it.kind == ServiceSettingKind.SERVER_URL }.forEach { ServiceField(it, settings) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle(Res.string.api_keys)
            Text(
                stringResource(Res.string.api_keys_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            services.filter { it.kind == ServiceSettingKind.API_KEY }.forEach { ServiceField(it, settings) }
            Spacer(Modifier.height(16.dp))
        }
    }

@Composable
private fun SectionTitle(title: StringResource) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** Saves when the keyboard's done action is used or the field loses focus. An empty field means the default. */
@Composable
private fun ServiceField(
    service: ServiceSetting,
    settings: SettingsStore,
) {
    val current by settings.serviceValue(service.id).collectAsStateWithLifecycle()
    var text by rememberSaveable(current) { mutableStateOf(current.orEmpty()) }
    val isUrl = service.kind == ServiceSettingKind.SERVER_URL
    val trimmed = text.trim().let { if (isUrl) it.trimEnd('/') else it }
    val valid = !isUrl || trimmed.isEmpty() || trimmed.startsWith("https://") || trimmed.startsWith("http://")
    val focusManager = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current

    fun save() {
        if (valid && trimmed != current.orEmpty()) settings.setServiceValue(service.id, trimmed)
    }

    val label =
        if (isUrl) {
            stringResource(Res.string.service_server, stringResource(service.role.label), service.provider)
        } else {
            stringResource(Res.string.service_key, service.provider)
        }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        placeholder = service.default?.let { default -> { Text(default) } },
        isError = !valid,
        supportingText = if (valid) null else ({ Text(stringResource(Res.string.server_invalid)) }),
        trailingIcon =
            service.helpUrl?.let { url ->
                {
                    IconButton(onClick = { uriHandler.openUri(url) }) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(Res.string.service_help, service.provider))
                    }
                }
            },
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(keyboardType = if (isUrl) KeyboardType.Uri else KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions =
            KeyboardActions(onDone = {
                save()
                focusManager.clearFocus()
            }),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .onFocusChanged { if (!it.isFocused) save() },
    )
}

private val ServiceRole.label: StringResource
    get() =
        when (this) {
            ServiceRole.TRANSIT -> Res.string.role_transit
            ServiceRole.STREET_ROUTING -> Res.string.role_street_routing
            ServiceRole.ACTIVE_ROUTING -> Res.string.role_active_routing
            ServiceRole.ROAD_EVENTS -> Res.string.role_road_events
            ServiceRole.PLACE_DETAILS -> Res.string.role_place_details
            ServiceRole.NEARBY_SEARCH -> Res.string.role_nearby_search
            ServiceRole.CHARGING_STATIONS -> Res.string.role_charging_stations
            ServiceRole.FUEL_PRICES -> Res.string.role_fuel_prices
            ServiceRole.RAIN_RADAR -> Res.string.role_rain_radar
            ServiceRole.WEATHER_WARNINGS -> Res.string.role_weather_warnings
            ServiceRole.REVIEWS -> Res.string.role_reviews
            ServiceRole.FLIGHTS -> Res.string.role_flights
            ServiceRole.WEATHER_FORECAST -> Res.string.role_weather_forecast
        }
private val CacheSizes = listOf(50, 100, 250, 500, 1000)
