package nz.eloque.openmaps.core.model

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/** Kinds of public transport a route may use, as offered to the user. */
enum class ModeGroup(
    val modes: Set<TransportMode>,
) {
    LONG_DISTANCE_TRAIN(setOf(TransportMode.HIGHSPEED_RAIL, TransportMode.LONG_DISTANCE_RAIL, TransportMode.NIGHT_RAIL)),
    REGIONAL_TRAIN(setOf(TransportMode.REGIONAL_RAIL, TransportMode.SUBURBAN)),
    SUBWAY_TRAM(setOf(TransportMode.SUBWAY, TransportMode.TRAM)),
    BUS(setOf(TransportMode.BUS, TransportMode.COACH)),
    FERRY(setOf(TransportMode.FERRY)),
}

enum class WalkingSpeed { SLOW, NORMAL, FAST }

/** How the user wants to travel, one tab each in directions. */
enum class TravelMode { TRANSIT, CAR, WALK, BIKE }

data class RouteOptions(
    val modes: Set<ModeGroup> = ModeGroup.entries.toSet(),
    /** Null for no limit. */
    val maxTransfers: Int? = null,
    val walkingSpeed: WalkingSpeed = WalkingSpeed.NORMAL,
    val wheelchair: Boolean = false,
    /** Whether shared bikes and scooters may take the user to and from public transport. */
    val sharedVehicles: Boolean = false,
    /** Only connections whose vehicles are known to carry bikes. */
    val bikeOnBoard: Boolean = false,
    /** Leaves out trains and buses that can only be taken with a reservation. */
    val avoidReservations: Boolean = false,
    val avoidTolls: Boolean = false,
    val avoidMotorways: Boolean = false,
    val avoidFerries: Boolean = false,
    val vehicle: Vehicle = Vehicle.CAR,
    /** Only used for [Vehicle.TRUCK]. */
    val truck: VehicleSize = VehicleSize(),
    val bike: BikeType = BikeType.HYBRID,
) {
    val isDefault: Boolean
        get() = this == RouteOptions()

    val transitIsDefault: Boolean
        get() =
            copy(
                avoidTolls = false,
                avoidMotorways = false,
                avoidFerries = false,
                vehicle = Vehicle.CAR,
                truck = VehicleSize(),
                bike = BikeType.HYBRID,
            ).isDefault

    val drivingIsDefault: Boolean
        get() = !avoidTolls && !avoidMotorways && !avoidFerries && vehicle == Vehicle.CAR

    val cyclingIsDefault: Boolean
        get() = bike == BikeType.HYBRID
}

/** What the user drives, which decides the roads a route may use. */
enum class Vehicle { CAR, MOTORCYCLE, MOPED, TRUCK }

/** Dimensions in meters and weight in tonnes, defaulting to a large truck. */
data class VehicleSize(
    val height: Double = 4.11,
    val width: Double = 2.6,
    val length: Double = 21.64,
    val weight: Double = 21.77,
)

/** What the user rides, from fast on smooth roads to slow on rough ground. */
enum class BikeType { ROAD, HYBRID, CROSS, MOUNTAIN }

enum class DistanceUnit { METRIC, IMPERIAL }

enum class MapLayer { TERRAIN, TRANSIT, CYCLING, HIKING, ROADWORKS, BUILDINGS_3D, SEAMARKS, RAIN_RADAR, CLOUDS, VEHICLES }

interface SettingsStore {
    val routeOptions: StateFlow<RouteOptions>

    fun setRouteOptions(options: RouteOptions)

    /** The directions tab used last. */
    val travelMode: StateFlow<TravelMode>

    fun setTravelMode(mode: TravelMode)

    val distanceUnit: StateFlow<DistanceUnit>

    fun setDistanceUnit(unit: DistanceUnit)

    /** Whether navigation plays a sound when driving clearly above the speed limit. */
    val speedingAlert: StateFlow<Boolean>

    fun setSpeedingAlert(enabled: Boolean)

    /** The fuel whose price gas station results show, null until the user picks one. */
    val preferredFuel: StateFlow<Fuel?>

    fun setPreferredFuel(fuel: Fuel?)

    /** Optional layers drawn over the base map. */
    val mapLayers: StateFlow<Set<MapLayer>>

    fun setMapLayers(layers: Set<MapLayer>)

    val radarOptions: StateFlow<RadarOptions>

    fun setRadarOptions(options: RadarOptions)

    /** Where the user last left the car, null when not remembered. */
    val parkedCar: StateFlow<ParkedCar?>

    fun setParkedCar(car: ParkedCar?)

    /** The user's value for the [ServiceSetting] with [id], or null to use its default. */
    fun serviceValue(id: String): StateFlow<String?>

    /** Blank values reset the setting to its default. */
    fun setServiceValue(
        id: String,
        value: String?,
    )
}

data class ParkedCar(
    val location: LatLng,
    val since: Instant,
)
