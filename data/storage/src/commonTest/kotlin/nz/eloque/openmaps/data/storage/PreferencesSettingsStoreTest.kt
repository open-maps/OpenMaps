package nz.eloque.openmaps.data.storage

import com.russhwolf.settings.MapSettings
import nz.eloque.openmaps.core.model.BikeType
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.ModeGroup
import nz.eloque.openmaps.core.model.ParkedCar
import nz.eloque.openmaps.core.model.RouteOptions
import nz.eloque.openmaps.core.model.TravelMode
import nz.eloque.openmaps.core.model.Vehicle
import nz.eloque.openmaps.core.model.VehicleSize
import nz.eloque.openmaps.core.model.WalkingSpeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PreferencesSettingsStoreTest {
    @Test
    fun defaultsWhenEmpty() {
        assertEquals(RouteOptions(), PreferencesSettingsStore(MapSettings()).routeOptions.value)
    }

    @Test
    fun remembersAndForgetsTheParkedCar() {
        val settings = MapSettings()
        val car = ParkedCar(LatLng(49.00691, 8.4037), Instant.fromEpochSeconds(1_800_000_000))
        PreferencesSettingsStore(settings).setParkedCar(car)
        assertEquals(car, PreferencesSettingsStore(settings).parkedCar.value)
        PreferencesSettingsStore(settings).setParkedCar(null)
        assertEquals(null, PreferencesSettingsStore(settings).parkedCar.value)
    }

    @Test
    fun remembersTheTravelMode() {
        val settings = MapSettings()
        assertEquals(TravelMode.TRANSIT, PreferencesSettingsStore(settings).travelMode.value)
        PreferencesSettingsStore(settings).setTravelMode(TravelMode.BIKE)
        assertEquals(TravelMode.BIKE, PreferencesSettingsStore(settings).travelMode.value)
    }

    @Test
    fun roundTripsRouteOptions() {
        val settings = MapSettings()
        val options =
            RouteOptions(setOf(ModeGroup.BUS, ModeGroup.FERRY), maxTransfers = 1, WalkingSpeed.SLOW, wheelchair = true, bikeOnBoard = true)
                .copy(extraTransferMinutes = 10, vehicle = Vehicle.TRUCK, truck = VehicleSize(3.2, 2.3, 7.5, 3.5), bike = BikeType.MOUNTAIN)
        PreferencesSettingsStore(settings).setRouteOptions(options)
        assertEquals(options, PreferencesSettingsStore(settings).routeOptions.value)
    }

    @Test
    fun unknownStoredValuesFallBackToDefaults() {
        val settings = MapSettings("route.modes" to "BUS,HOVERCRAFT", "route.walkingSpeed" to "WARP")
        val options = PreferencesSettingsStore(settings).routeOptions.value
        assertEquals(setOf(ModeGroup.BUS), options.modes)
        assertEquals(WalkingSpeed.NORMAL, options.walkingSpeed)
    }
}
