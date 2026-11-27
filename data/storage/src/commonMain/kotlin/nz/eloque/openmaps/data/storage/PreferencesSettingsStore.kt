package nz.eloque.openmaps.data.storage

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nz.eloque.openmaps.core.model.BikeType
import nz.eloque.openmaps.core.model.DistanceUnit
import nz.eloque.openmaps.core.model.Fuel
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.MapLayer
import nz.eloque.openmaps.core.model.ModeGroup
import nz.eloque.openmaps.core.model.ParkedCar
import nz.eloque.openmaps.core.model.RadarOptions
import nz.eloque.openmaps.core.model.RouteOptions
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.TravelMode
import nz.eloque.openmaps.core.model.Vehicle
import nz.eloque.openmaps.core.model.VehicleSize
import nz.eloque.openmaps.core.model.WalkingSpeed
import kotlin.time.Instant

/** Values this version does not know, such as a mode group added later, are ignored on read. */
internal class PreferencesSettingsStore(
    private val settings: Settings,
) : SettingsStore {
    private val _routeOptions = MutableStateFlow(readRouteOptions())
    override val routeOptions: StateFlow<RouteOptions> = _routeOptions.asStateFlow()

    override fun setRouteOptions(options: RouteOptions) {
        settings.putString(MODES, options.modes.joinToString(",") { it.name })
        settings.putInt(MAX_TRANSFERS, options.maxTransfers ?: NO_LIMIT)
        settings.putString(WALKING_SPEED, options.walkingSpeed.name)
        settings.putInt(EXTRA_TRANSFER_MINUTES, options.extraTransferMinutes)
        settings.putBoolean(WHEELCHAIR, options.wheelchair)
        settings.putBoolean(SHARED_VEHICLES, options.sharedVehicles)
        settings.putBoolean(BIKE_ON_BOARD, options.bikeOnBoard)
        settings.putBoolean(AVOID_RESERVATIONS, options.avoidReservations)
        settings.putBoolean(AVOID_TOLLS, options.avoidTolls)
        settings.putBoolean(AVOID_MOTORWAYS, options.avoidMotorways)
        settings.putBoolean(AVOID_FERRIES, options.avoidFerries)
        settings.putString(VEHICLE, options.vehicle.name)
        settings.putString(TRUCK_SIZE, options.truck.let { "${it.height},${it.width},${it.length},${it.weight}" })
        settings.putString(BIKE_TYPE, options.bike.name)
        _routeOptions.value = options
    }

    private val _travelMode =
        MutableStateFlow(TravelMode.entries.firstOrNull { it.name == settings.getStringOrNull(TRAVEL_MODE) } ?: TravelMode.TRANSIT)
    override val travelMode: StateFlow<TravelMode> = _travelMode.asStateFlow()

    override fun setTravelMode(mode: TravelMode) {
        settings.putString(TRAVEL_MODE, mode.name)
        _travelMode.value = mode
    }

    private val _distanceUnit =
        MutableStateFlow(DistanceUnit.entries.firstOrNull { it.name == settings.getStringOrNull(DISTANCE_UNIT) } ?: DistanceUnit.METRIC)
    override val distanceUnit: StateFlow<DistanceUnit> = _distanceUnit.asStateFlow()

    override fun setDistanceUnit(unit: DistanceUnit) {
        settings.putString(DISTANCE_UNIT, unit.name)
        _distanceUnit.value = unit
    }

    private val _speedingAlert = MutableStateFlow(settings.getBoolean(SPEEDING_ALERT, false))
    override val speedingAlert: StateFlow<Boolean> = _speedingAlert.asStateFlow()

    override fun setSpeedingAlert(enabled: Boolean) {
        settings.putBoolean(SPEEDING_ALERT, enabled)
        _speedingAlert.value = enabled
    }

    private val _mapCacheMegabytes = MutableStateFlow(settings.getInt(MAP_CACHE_MEGABYTES, DEFAULT_MAP_CACHE_MEGABYTES))
    override val mapCacheMegabytes: StateFlow<Int> = _mapCacheMegabytes.asStateFlow()

    override fun setMapCacheMegabytes(megabytes: Int) {
        settings.putInt(MAP_CACHE_MEGABYTES, megabytes)
        _mapCacheMegabytes.value = megabytes
    }

    private val _preferredFuel = MutableStateFlow(Fuel.entries.firstOrNull { it.name == settings.getStringOrNull(PREFERRED_FUEL) })
    override val preferredFuel: StateFlow<Fuel?> = _preferredFuel.asStateFlow()

    override fun setPreferredFuel(fuel: Fuel?) {
        if (fuel == null) settings.remove(PREFERRED_FUEL) else settings.putString(PREFERRED_FUEL, fuel.name)
        _preferredFuel.value = fuel
    }

    private val _mapLayers =
        MutableStateFlow(
            settings
                .getStringOrNull(MAP_LAYERS)
                ?.split(',')
                ?.mapNotNull { name -> MapLayer.entries.firstOrNull { it.name == name } }
                ?.toSet()
                ?: setOf(MapLayer.ROADWORKS, MapLayer.BUILDINGS_3D),
        )
    override val mapLayers: StateFlow<Set<MapLayer>> = _mapLayers.asStateFlow()

    override fun setMapLayers(layers: Set<MapLayer>) {
        settings.putString(MAP_LAYERS, layers.joinToString(",") { it.name })
        _mapLayers.value = layers
    }

    private val _radarOptions =
        MutableStateFlow(
            RadarOptions(
                arrows = settings.getBoolean(RADAR_ARROWS, true),
                lightning = settings.getBoolean(RADAR_LIGHTNING, true),
                stormCells = settings.getBoolean(RADAR_STORM_CELLS, false),
                colourScheme = settings.getInt(RADAR_COLOURS, RadarOptions.DEFAULT_COLOUR_SCHEME),
            ),
        )
    override val radarOptions: StateFlow<RadarOptions> = _radarOptions.asStateFlow()

    override fun setRadarOptions(options: RadarOptions) {
        settings.putBoolean(RADAR_ARROWS, options.arrows)
        settings.putBoolean(RADAR_LIGHTNING, options.lightning)
        settings.putBoolean(RADAR_STORM_CELLS, options.stormCells)
        settings.putInt(RADAR_COLOURS, options.colourScheme)
        _radarOptions.value = options
    }

    private val _parkedCar =
        MutableStateFlow(
            settings.getStringOrNull(PARKED_CAR)?.split(",")?.let { parts ->
                val lat = parts.getOrNull(0)?.toDoubleOrNull()
                val lon = parts.getOrNull(1)?.toDoubleOrNull()
                val since = parts.getOrNull(2)?.toLongOrNull()
                if (lat == null || lon == null || since == null) null else ParkedCar(LatLng(lat, lon), Instant.fromEpochSeconds(since))
            },
        )
    override val parkedCar: StateFlow<ParkedCar?> = _parkedCar.asStateFlow()

    override fun setParkedCar(car: ParkedCar?) {
        if (car == null) {
            settings.remove(PARKED_CAR)
        } else {
            settings.putString(PARKED_CAR, "${car.location.latitude},${car.location.longitude},${car.since.epochSeconds}")
        }
        _parkedCar.value = car
    }

    private val serviceValues = mutableMapOf<String, MutableStateFlow<String?>>()

    override fun serviceValue(id: String): StateFlow<String?> = serviceState(id).asStateFlow()

    override fun setServiceValue(
        id: String,
        value: String?,
    ) {
        val cleaned = value?.trim()?.takeIf { it.isNotEmpty() }
        if (cleaned == null) settings.remove(id) else settings.putString(id, cleaned)
        serviceState(id).value = cleaned
    }

    private fun serviceState(id: String) = serviceValues.getOrPut(id) { MutableStateFlow(settings.getStringOrNull(id)) }

    private fun readTruckSize(): VehicleSize? {
        val parts = settings.getStringOrNull(TRUCK_SIZE)?.split(",")?.map { it.toDoubleOrNull() ?: return null } ?: return null
        if (parts.size != 4) return null
        return VehicleSize(height = parts[0], width = parts[1], length = parts[2], weight = parts[3])
    }

    private fun readRouteOptions(): RouteOptions {
        val defaults = RouteOptions()
        val modes =
            settings
                .getStringOrNull(MODES)
                ?.split(',')
                ?.mapNotNull { name -> ModeGroup.entries.firstOrNull { it.name == name } }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
        return RouteOptions(
            modes = modes ?: defaults.modes,
            maxTransfers = settings.getInt(MAX_TRANSFERS, NO_LIMIT).takeIf { it >= 0 },
            walkingSpeed =
                WalkingSpeed.entries.firstOrNull { it.name == settings.getStringOrNull(WALKING_SPEED) } ?: defaults.walkingSpeed,
            extraTransferMinutes = settings.getInt(EXTRA_TRANSFER_MINUTES, defaults.extraTransferMinutes).coerceAtLeast(0),
            wheelchair = settings.getBoolean(WHEELCHAIR, defaults.wheelchair),
            sharedVehicles = settings.getBoolean(SHARED_VEHICLES, defaults.sharedVehicles),
            bikeOnBoard = settings.getBoolean(BIKE_ON_BOARD, defaults.bikeOnBoard),
            avoidReservations = settings.getBoolean(AVOID_RESERVATIONS, defaults.avoidReservations),
            avoidTolls = settings.getBoolean(AVOID_TOLLS, defaults.avoidTolls),
            avoidMotorways = settings.getBoolean(AVOID_MOTORWAYS, defaults.avoidMotorways),
            avoidFerries = settings.getBoolean(AVOID_FERRIES, defaults.avoidFerries),
            vehicle = Vehicle.entries.firstOrNull { it.name == settings.getStringOrNull(VEHICLE) } ?: defaults.vehicle,
            truck = readTruckSize() ?: defaults.truck,
            bike = BikeType.entries.firstOrNull { it.name == settings.getStringOrNull(BIKE_TYPE) } ?: defaults.bike,
        )
    }

    private companion object {
        const val MODES = "route.modes"
        const val MAX_TRANSFERS = "route.maxTransfers"
        const val WALKING_SPEED = "route.walkingSpeed"
        const val EXTRA_TRANSFER_MINUTES = "route.extraTransferMinutes"
        const val WHEELCHAIR = "route.wheelchair"
        const val SHARED_VEHICLES = "route.sharedVehicles"
        const val BIKE_ON_BOARD = "route.bikeOnBoard"
        const val AVOID_RESERVATIONS = "route.avoidReservations"
        const val AVOID_TOLLS = "route.avoidTolls"
        const val AVOID_MOTORWAYS = "route.avoidMotorways"
        const val AVOID_FERRIES = "route.avoidFerries"
        const val VEHICLE = "route.vehicle"
        const val TRUCK_SIZE = "route.truckSize"
        const val BIKE_TYPE = "route.bikeType"
        const val NO_LIMIT = -1
        const val TRAVEL_MODE = "route.travelMode"
        const val DISTANCE_UNIT = "units.distance"
        const val SPEEDING_ALERT = "navigation.speeding_alert"
        const val MAP_CACHE_MEGABYTES = "map.cache_megabytes"
        const val DEFAULT_MAP_CACHE_MEGABYTES = 100
        const val PREFERRED_FUEL = "places.preferred_fuel"
        const val MAP_LAYERS = "map.layers"
        const val RADAR_ARROWS = "map.radar.arrows"
        const val RADAR_LIGHTNING = "map.radar.lightning"
        const val RADAR_STORM_CELLS = "map.radar.cells"
        const val RADAR_COLOURS = "map.radar.colours"
        const val PARKED_CAR = "parking.car"
    }
}
