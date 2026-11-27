package nz.eloque.openmaps.data.flights

import com.jsoizo.kotlincsv.csvReader
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nz.eloque.openmaps.core.model.Airport
import nz.eloque.openmaps.core.model.LatLng

/** Routes, airports, airlines and aircraft models from Virtual Radar Server's standing data, one small file at a time. */
internal class StandingData(
    private val client: HttpClient,
    private val baseUrl: Flow<String?>,
) {
    private val tables = mutableMapOf<String, Map<String, Map<String, String>>>()
    private val lock = Mutex()

    /** The airports a flight with [callsign] usually flies between, first to last. */
    suspend fun route(callsign: String): List<String>? {
        val airline = callsign.takeWhile { it.isLetter() }.takeIf { it.length == 3 } ?: return null
        val row = table("routes/schema-01/${airline[0]}/$airline-all.csv")[callsign] ?: return null
        return row["AirportCodes"]?.split('-')?.filter { it.isNotBlank() }?.takeIf { it.size >= 2 }
    }

    suspend fun airport(icao: String): Airport? {
        if (icao.length < 2) return null
        val row = table("airports/schema-01/${icao[0]}/${icao.take(2)}.csv")[icao] ?: return null
        val latitude = row["Latitude"]?.toDoubleOrNull() ?: return null
        val longitude = row["Longitude"]?.toDoubleOrNull() ?: return null
        return Airport(
            icao = icao,
            iata = row["IATA"]?.takeIf { it.isNotBlank() },
            name = row["Name"]?.takeIf { it.isNotBlank() } ?: icao,
            city = row["Location"]?.takeIf { it.isNotBlank() },
            location = LatLng(latitude, longitude),
        )
    }

    /** The name of the airline whose ICAO code starts [callsign]. */
    suspend fun airline(callsign: String): String? {
        val code = callsign.takeWhile { it.isLetter() }.takeIf { it.length == 3 } ?: return null
        return table("airlines/schema-01/airlines.csv", key = "ICAO")[code]?.get("Name")?.takeIf { it.isNotBlank() }
    }

    suspend fun model(typeCode: String): String? {
        val row = table("model-type/schema-01/${typeCode[0]}.csv")[typeCode] ?: return null
        return listOfNotNull(row["Manufacturer"], row["Model"])
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
    }

    private suspend fun table(
        path: String,
        key: String? = null,
    ): Map<String, Map<String, String>> =
        lock.withLock {
            val id = "$path#$key"
            tables[id]?.let { return@withLock it }
            val base = baseUrl.first()?.takeIf { it.isNotBlank() }?.trimEnd('/') ?: return@withLock emptyMap()
            val text =
                try {
                    client.get("$base/$path").body<String>()
                } catch (_: ClientRequestException) {
                    ""
                }
            parseTable(text, key).also { tables[id] = it }
        }
}

internal fun parseTable(
    text: String,
    key: String? = null,
): Map<String, Map<String, String>> {
    val rows = csvReader().readAll(text.removePrefix(""))
    val header = rows.firstOrNull() ?: return emptyMap()
    val keyIndex = key?.let(header::indexOf)?.takeIf { it >= 0 } ?: 0
    return rows
        .drop(1)
        .filter { it.size == header.size && it[keyIndex].isNotBlank() }
        .associate { row -> row[keyIndex] to header.zip(row.map(::unescape)).toMap() }
}

private fun unescape(value: String) =
    value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
