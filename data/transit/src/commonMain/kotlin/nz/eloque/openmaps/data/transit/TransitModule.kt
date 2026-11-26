package nz.eloque.openmaps.data.transit

import nz.eloque.openmaps.core.model.Geocoder
import nz.eloque.openmaps.core.model.LiveVehicles
import nz.eloque.openmaps.core.model.ServiceRole
import nz.eloque.openmaps.core.model.ServiceSetting
import nz.eloque.openmaps.core.model.ServiceSettingKind
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.core.model.SharedMobility
import nz.eloque.openmaps.core.model.TransitRepository
import org.koin.dsl.module

const val TRANSITOUS_API = "https://api.transitous.org/api"

/** A self-hosted MOTIS server to use instead of Transitous. Its id is the key earlier versions stored the address under. */
val TransitServer =
    ServiceSetting("server.transit", ServiceRole.TRANSIT, ServiceSettingKind.SERVER_URL, "MOTIS", TRANSITOUS_API, "https://transitous.org")

/** The settings this module's sources read. */
val transitServiceSettings = listOf(TransitServer)

/** Uses the server from [TransitServer] when one is set, otherwise Transitous. */
fun transitModule(language: () -> String? = { null }) =
    module {
        single<TransitRepository> {
            val settings = get<SettingsStore>()
            MotisTransitRepository(get(), { settings.serviceValue(TransitServer.id).value ?: TRANSITOUS_API }, language)
        }
        single<SharedMobility> {
            val settings = get<SettingsStore>()
            MotisSharedMobility(get()) { settings.serviceValue(TransitServer.id).value ?: TRANSITOUS_API }
        }
        single<LiveVehicles> {
            val settings = get<SettingsStore>()
            MotisLiveVehicles(get()) { settings.serviceValue(TransitServer.id).value ?: TRANSITOUS_API }
        }
        single<Geocoder> {
            val settings = get<SettingsStore>()
            MotisGeocoder(get(), { settings.serviceValue(TransitServer.id).value ?: TRANSITOUS_API }, language)
        }
    }
