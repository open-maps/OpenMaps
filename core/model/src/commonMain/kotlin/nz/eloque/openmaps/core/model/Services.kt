package nz.eloque.openmaps.core.model

/** What a data source provides, so settings can say what a value changes. */
enum class ServiceRole {
    TRANSIT,
    STREET_ROUTING,
    ACTIVE_ROUTING,
    ROAD_EVENTS,
    PLACE_DETAILS,
    NEARBY_SEARCH,
    CHARGING_STATIONS,
    FUEL_PRICES,
    RAIN_RADAR,
    WEATHER_WARNINGS,
    WEATHER_FORECAST,
    REVIEWS,
    FLIGHTS,
}

enum class ServiceSettingKind {
    /** The address of a self-hosted server. */
    SERVER_URL,

    /** A secret that identifies the user to the service. */
    API_KEY,
}

/** Something about a data source the user may change, declared by the module that uses the source. */
data class ServiceSetting(
    /** Where [SettingsStore.serviceValue] keeps the value. */
    val id: String,
    val role: ServiceRole,
    val kind: ServiceSettingKind,
    /** The service as people know it, such as `Photon`. */
    val provider: String,
    /** The value used while none is set, shown as a hint. Null when it should not be shown, as for a built-in key. */
    val default: String?,
    /** Where to read about the service or get a key. */
    val helpUrl: String? = null,
)

/** All [ServiceSetting]s of the data sources in use. */
data class ServiceCatalog(
    val settings: List<ServiceSetting>,
)
