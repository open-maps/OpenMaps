package nz.eloque.openmaps.data.flights

import kotlinx.coroutines.flow.map
import nz.eloque.openmaps.core.model.AirTraffic
import nz.eloque.openmaps.core.model.Aircraft
import nz.eloque.openmaps.core.model.FlightInfo
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.ServiceRole
import nz.eloque.openmaps.core.model.ServiceSetting
import nz.eloque.openmaps.core.model.ServiceSettingKind
import nz.eloque.openmaps.core.model.SettingsStore
import org.koin.dsl.module

/** An adsb.lol compatible server for aircraft positions. */
val FlightsServer =
    ServiceSetting(
        "server.flights",
        ServiceRole.FLIGHTS,
        ServiceSettingKind.SERVER_URL,
        "adsb.lol",
        default = ADSB_LOL,
        helpUrl = "https://www.adsb.lol/docs/open-data/api",
    )

/** Where Virtual Radar Server's standing data is served, for routes, airports and aircraft models. */
val FlightDataServer =
    ServiceSetting(
        "server.flightData",
        ServiceRole.FLIGHTS,
        ServiceSettingKind.SERVER_URL,
        "VRS standing data",
        default = STANDING_DATA,
        helpUrl = "https://github.com/vradarserver/standing-data",
    )

internal const val ADSB_LOL = "https://api.adsb.lol"
internal const val STANDING_DATA = "https://raw.githubusercontent.com/vradarserver/standing-data/main"

/** The settings this module's sources read. */
val flightsServiceSettings = listOf(FlightsServer, FlightDataServer)

val flightsModule =
    module {
        single<AirTraffic> {
            val settings = get<SettingsStore>()
            FlightSources(
                AdsbLol(get(), settings.serviceValue(FlightsServer.id).map { it ?: ADSB_LOL }),
                StandingData(get(), settings.serviceValue(FlightDataServer.id).map { it ?: STANDING_DATA }),
            )
        }
    }

internal class FlightSources(
    private val positions: AdsbLol,
    private val data: StandingData,
) : AirTraffic {
    override suspend fun near(
        center: LatLng,
        radiusMeters: Double,
    ): List<Aircraft> = positions.near(center, radiusMeters)

    override suspend fun aircraft(id: String): Aircraft? = positions.byAddress(id)

    override suspend fun flight(number: String): List<Aircraft> {
        val wanted = number.uppercase().filterNot { it.isWhitespace() }
        if (AirlineCallsign.matches(wanted)) return positions.byCallsign(wanted)
        val (airline, digits, suffix) = IataFlight.matchEntire(wanted)?.destructured ?: return emptyList()
        val callsigns = data.airlinesWithIata(airline).take(MAX_AIRLINES).map { it + digits.trimStart('0') + suffix }
        return callsigns.flatMap { positions.byCallsign(it) }.distinctBy { it.id }
    }

    override suspend fun info(aircraft: Aircraft): FlightInfo {
        val callsign = aircraft.callsign?.takeIf { AirlineCallsign.matches(it) }
        val stops = callsign?.let { data.route(it) }?.mapNotNull { data.airport(it) }.orEmpty()
        val leg = stops.zipWithNext().minByOrNull { (from, to) -> detour(from.location, aircraft.location, to.location) }
        return FlightInfo(
            airline = callsign?.let { data.airline(it) },
            model = aircraft.typeCode?.let { data.model(it) },
            origin = leg?.first,
            destination = leg?.second,
        )
    }
}

/** An airline's ICAO code followed by a flight number, unlike the registrations private flights use. */
private val AirlineCallsign = Regex("[A-Z]{3}[0-9][0-9A-Z]*")

/** An IATA airline code, which has at least one letter, then the flight number. */
private val IataFlight = Regex("((?=[A-Z]|[0-9][A-Z])[A-Z0-9]{2})([0-9]{1,4})([A-Z]?)")

// Several airlines, mostly defunct, can share an IATA code. Each costs one request.
private const val MAX_AIRLINES = 3

private fun detour(
    from: LatLng,
    via: LatLng,
    to: LatLng,
) = from.distanceTo(via) + via.distanceTo(to) - from.distanceTo(to)
