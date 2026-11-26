package nz.eloque.openmaps.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Moped
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.settings.SettingsSwitch
import nz.eloque.openmaps.core.model.BikeType
import nz.eloque.openmaps.core.model.ModeGroup
import nz.eloque.openmaps.core.model.RouteOptions
import nz.eloque.openmaps.core.model.TravelMode
import nz.eloque.openmaps.core.model.Vehicle
import nz.eloque.openmaps.core.model.VehicleSize
import nz.eloque.openmaps.core.model.WalkingSpeed
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.avoid_ferries
import nz.eloque.openmaps.ui.resources.avoid_motorways
import nz.eloque.openmaps.ui.resources.avoid_tolls
import nz.eloque.openmaps.ui.resources.bike_cross
import nz.eloque.openmaps.ui.resources.bike_hybrid
import nz.eloque.openmaps.ui.resources.bike_mountain
import nz.eloque.openmaps.ui.resources.bike_road
import nz.eloque.openmaps.ui.resources.mode_bus
import nz.eloque.openmaps.ui.resources.mode_ferry
import nz.eloque.openmaps.ui.resources.mode_long_distance
import nz.eloque.openmaps.ui.resources.mode_regional
import nz.eloque.openmaps.ui.resources.mode_subway_tram
import nz.eloque.openmaps.ui.resources.options_avoid_reservations
import nz.eloque.openmaps.ui.resources.options_avoid_reservations_detail
import nz.eloque.openmaps.ui.resources.options_bike
import nz.eloque.openmaps.ui.resources.options_bike_on_board
import nz.eloque.openmaps.ui.resources.options_bike_on_board_detail
import nz.eloque.openmaps.ui.resources.options_driving
import nz.eloque.openmaps.ui.resources.options_shared_vehicles
import nz.eloque.openmaps.ui.resources.options_shared_vehicles_detail
import nz.eloque.openmaps.ui.resources.options_transfer_time
import nz.eloque.openmaps.ui.resources.options_transfers
import nz.eloque.openmaps.ui.resources.options_transport
import nz.eloque.openmaps.ui.resources.options_vehicle
import nz.eloque.openmaps.ui.resources.options_walking
import nz.eloque.openmaps.ui.resources.options_wheelchair
import nz.eloque.openmaps.ui.resources.options_wheelchair_detail
import nz.eloque.openmaps.ui.resources.transfer_time_extra
import nz.eloque.openmaps.ui.resources.transfer_time_usual
import nz.eloque.openmaps.ui.resources.transfers_any
import nz.eloque.openmaps.ui.resources.transfers_direct
import nz.eloque.openmaps.ui.resources.transfers_up_to
import nz.eloque.openmaps.ui.resources.truck_height
import nz.eloque.openmaps.ui.resources.truck_length
import nz.eloque.openmaps.ui.resources.truck_weight
import nz.eloque.openmaps.ui.resources.truck_width
import nz.eloque.openmaps.ui.resources.vehicle_car
import nz.eloque.openmaps.ui.resources.vehicle_moped
import nz.eloque.openmaps.ui.resources.vehicle_motorcycle
import nz.eloque.openmaps.ui.resources.vehicle_truck
import nz.eloque.openmaps.ui.resources.walking_fast
import nz.eloque.openmaps.ui.resources.walking_normal
import nz.eloque.openmaps.ui.resources.walking_slow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val TransferChoices = listOf(null, 0, 1, 2)

private val ExtraTransferChoices = listOf(0, 5, 10, 15)

/** Edits how routes are planned, only for [mode] when set. The last selected kind of transport cannot be switched off. */
@Composable
fun RouteOptionsEditor(
    options: RouteOptions,
    onChange: (RouteOptions) -> Unit,
    modifier: Modifier = Modifier,
    mode: TravelMode? = null,
) {
    Column(modifier) {
        if (mode == null || mode == TravelMode.TRANSIT) TransitOptions(options, onChange)
        if (mode == null || mode == TravelMode.BIKE) {
            OptionGroup(Res.string.options_bike) {
                BikeType.entries.forEach { bike ->
                    FilterChip(
                        selected = options.bike == bike,
                        onClick = { onChange(options.copy(bike = bike)) },
                        label = { Text(stringResource(bike.label)) },
                    )
                }
            }
        }
        if (mode == null || mode == TravelMode.CAR) {
            OptionGroup(Res.string.options_vehicle) {
                Vehicle.entries.forEach { vehicle ->
                    FilterChip(
                        selected = options.vehicle == vehicle,
                        onClick = { onChange(options.copy(vehicle = vehicle)) },
                        label = { Text(stringResource(vehicle.label)) },
                        leadingIcon = { Icon(vehicle.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            if (options.vehicle == Vehicle.TRUCK) TruckSizeFields(options.truck) { onChange(options.copy(truck = it)) }
            OptionGroup(Res.string.options_driving) {
                FilterChip(
                    selected = options.avoidTolls,
                    onClick = { onChange(options.copy(avoidTolls = !options.avoidTolls)) },
                    label = { Text(stringResource(Res.string.avoid_tolls)) },
                )
                FilterChip(
                    selected = options.avoidMotorways,
                    onClick = { onChange(options.copy(avoidMotorways = !options.avoidMotorways)) },
                    label = { Text(stringResource(Res.string.avoid_motorways)) },
                )
                FilterChip(
                    selected = options.avoidFerries,
                    onClick = { onChange(options.copy(avoidFerries = !options.avoidFerries)) },
                    label = { Text(stringResource(Res.string.avoid_ferries)) },
                )
            }
        }
    }
}

@Composable
private fun TransitOptions(
    options: RouteOptions,
    onChange: (RouteOptions) -> Unit,
) {
    Column {
        OptionGroup(Res.string.options_transport) {
            ModeGroup.entries.forEach { group ->
                val selected = group in options.modes
                FilterChip(
                    selected = selected,
                    onClick = {
                        val modes = if (selected) options.modes - group else options.modes + group
                        if (modes.isNotEmpty()) onChange(options.copy(modes = modes))
                    },
                    label = { Text(stringResource(group.label)) },
                )
            }
        }
        OptionGroup(Res.string.options_transfers) {
            TransferChoices.forEach { choice ->
                FilterChip(
                    selected = options.maxTransfers == choice,
                    onClick = { onChange(options.copy(maxTransfers = choice)) },
                    label = {
                        Text(
                            when (choice) {
                                null -> stringResource(Res.string.transfers_any)
                                0 -> stringResource(Res.string.transfers_direct)
                                else -> stringResource(Res.string.transfers_up_to, choice)
                            },
                        )
                    },
                )
            }
        }
        OptionGroup(Res.string.options_transfer_time) {
            ExtraTransferChoices.forEach { minutes ->
                FilterChip(
                    selected = options.extraTransferMinutes == minutes,
                    onClick = { onChange(options.copy(extraTransferMinutes = minutes)) },
                    label = {
                        Text(
                            if (minutes == 0) {
                                stringResource(Res.string.transfer_time_usual)
                            } else {
                                stringResource(Res.string.transfer_time_extra, minutes)
                            },
                        )
                    },
                )
            }
        }
        OptionGroup(Res.string.options_walking) {
            WalkingSpeed.entries.forEach { speed ->
                FilterChip(
                    selected = options.walkingSpeed == speed,
                    onClick = { onChange(options.copy(walkingSpeed = speed)) },
                    label = { Text(stringResource(speed.label)) },
                )
            }
        }
        SettingsSwitch(
            title = stringResource(Res.string.options_wheelchair),
            subtitle = stringResource(Res.string.options_wheelchair_detail),
            checked = options.wheelchair,
            onCheckedChange = { onChange(options.copy(wheelchair = it)) },
        )
        SettingsSwitch(
            title = stringResource(Res.string.options_shared_vehicles),
            subtitle = stringResource(Res.string.options_shared_vehicles_detail),
            checked = options.sharedVehicles,
            onCheckedChange = { onChange(options.copy(sharedVehicles = it)) },
        )
        SettingsSwitch(
            title = stringResource(Res.string.options_bike_on_board),
            subtitle = stringResource(Res.string.options_bike_on_board_detail),
            checked = options.bikeOnBoard,
            onCheckedChange = { onChange(options.copy(bikeOnBoard = it)) },
        )
        SettingsSwitch(
            title = stringResource(Res.string.options_avoid_reservations),
            subtitle = stringResource(Res.string.options_avoid_reservations_detail),
            checked = options.avoidReservations,
            onCheckedChange = { onChange(options.copy(avoidReservations = it)) },
        )
    }
}

@Composable
private fun OptionGroup(
    title: StringResource,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

val ModeGroup.label: StringResource
    get() =
        when (this) {
            ModeGroup.LONG_DISTANCE_TRAIN -> Res.string.mode_long_distance
            ModeGroup.REGIONAL_TRAIN -> Res.string.mode_regional
            ModeGroup.SUBWAY_TRAM -> Res.string.mode_subway_tram
            ModeGroup.BUS -> Res.string.mode_bus
            ModeGroup.FERRY -> Res.string.mode_ferry
        }

private val WalkingSpeed.label: StringResource
    get() =
        when (this) {
            WalkingSpeed.SLOW -> Res.string.walking_slow
            WalkingSpeed.NORMAL -> Res.string.walking_normal
            WalkingSpeed.FAST -> Res.string.walking_fast
        }

/** The truck's size, kept as typed until it reads as a positive number. */
@Composable
private fun TruckSizeFields(
    size: VehicleSize,
    onChange: (VehicleSize) -> Unit,
) {
    FlowRow(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SizeField(Res.string.truck_height, size.height) { onChange(size.copy(height = it)) }
        SizeField(Res.string.truck_width, size.width) { onChange(size.copy(width = it)) }
        SizeField(Res.string.truck_length, size.length) { onChange(size.copy(length = it)) }
        SizeField(Res.string.truck_weight, size.weight) { onChange(size.copy(weight = it)) }
    }
}

@Composable
private fun SizeField(
    label: StringResource,
    value: Double,
    onChange: (Double) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            typed.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let(onChange)
        },
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(150.dp),
    )
}

internal val Vehicle.label: StringResource
    get() =
        when (this) {
            Vehicle.CAR -> Res.string.vehicle_car
            Vehicle.MOTORCYCLE -> Res.string.vehicle_motorcycle
            Vehicle.MOPED -> Res.string.vehicle_moped
            Vehicle.TRUCK -> Res.string.vehicle_truck
        }

internal val Vehicle.icon: ImageVector
    get() =
        when (this) {
            Vehicle.CAR -> Icons.Default.DirectionsCar
            Vehicle.MOTORCYCLE -> Icons.Default.TwoWheeler
            Vehicle.MOPED -> Icons.Default.Moped
            Vehicle.TRUCK -> Icons.Default.LocalShipping
        }

private val BikeType.label: StringResource
    get() =
        when (this) {
            BikeType.ROAD -> Res.string.bike_road
            BikeType.HYBRID -> Res.string.bike_hybrid
            BikeType.CROSS -> Res.string.bike_cross
            BikeType.MOUNTAIN -> Res.string.bike_mountain
        }
