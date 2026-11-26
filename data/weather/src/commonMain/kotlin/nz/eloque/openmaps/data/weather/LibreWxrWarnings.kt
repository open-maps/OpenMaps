package nz.eloque.openmaps.data.weather

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.WarningSeverity
import nz.eloque.openmaps.core.model.WeatherWarning
import nz.eloque.openmaps.core.model.WeatherWarnings
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Serializable
internal data class AlertsDto(
    val features: List<AlertFeatureDto> = emptyList(),
)

@Serializable
internal data class AlertFeatureDto(
    val properties: AlertDto,
)

@Serializable
internal data class AlertDto(
    val title: String = "",
    val severity: String? = null,
    val time: Long? = null,
    val expires: Long? = null,
    val description: String? = null,
    val regions: List<String> = emptyList(),
    val uri: String? = null,
)

/** Weather warnings from LibreWXR's alerts endpoint, which gathers the CAP feeds of national weather services. */
internal class LibreWxrWarnings(
    private val client: HttpClient,
    private val baseUrl: Flow<String?>,
    private val clock: Clock = Clock.System,
    private val keep: Duration = 10.minutes,
    private val language: () -> String? = { null },
) : WeatherWarnings {
    private val mutex = Mutex()
    private val cache = mutableMapOf<Pair<Long, Long>, Pair<Instant, List<WeatherWarning>>>()

    override suspend fun at(location: LatLng): List<WeatherWarning> {
        val base = baseUrl.first()?.takeIf { it.isNotBlank() }?.trimEnd('/') ?: return emptyList()
        // Warning areas are districts or larger, so nearby places share one answer.
        val key = (location.latitude * CELLS_PER_DEGREE).roundToLong() to (location.longitude * CELLS_PER_DEGREE).roundToLong()
        val now = clock.now()
        mutex.withLock { cache[key]?.takeIf { now - it.first < keep }?.let { return it.second } }
        val warnings =
            client
                .get("$base/v2/alerts") {
                    parameter("lat", location.latitude)
                    parameter("lon", location.longitude)
                    // Only the properties are read, so the areas come as coarse as the server allows.
                    parameter("simplify", SIMPLIFY_METRES)
                }.body<AlertsDto>()
                .features
                .map { it.properties }
                .inLanguage(language())
                .map { it.toWarning() }
                .filter { warning -> warning.end?.let { it > now } ?: true }
                .sortedWith(compareByDescending<WeatherWarning> { it.severity }.thenBy { it.start })
        mutex.withLock {
            cache.entries.removeAll { now - it.value.first >= keep }
            cache[key] = now to warnings
        }
        return warnings
    }
}

internal fun AlertDto.toWarning() =
    WeatherWarning(
        id = uri ?: "$title-$time",
        title = title.ifBlank { description.orEmpty() },
        severity =
            when (severity?.lowercase()) {
                "minor" -> WarningSeverity.MINOR
                "moderate" -> WarningSeverity.MODERATE
                "severe" -> WarningSeverity.SEVERE
                "extreme" -> WarningSeverity.EXTREME
                else -> WarningSeverity.UNKNOWN
            },
        start = time?.let(Instant::fromEpochSeconds),
        end = expires?.let(Instant::fromEpochSeconds),
        description = description?.takeIf { it.isNotBlank() && it != title },
        regions = regions.flatMap { it.split("; ") }.filter { it.isNotBlank() }.distinct(),
        source =
            uri?.substringAfter("://", "")?.substringBefore("/")?.removePrefix("feeds.")?.removePrefix("www.")?.takeIf {
                it.isNotBlank()
            },
    )

private const val CELLS_PER_DEGREE = 100.0
private const val SIMPLIFY_METRES = 50_000

/** Keeps one copy of warnings issued in several languages, preferring [language] and then English. */
internal fun List<AlertDto>.inLanguage(language: String?): List<AlertDto> {
    val preferred = listOfNotNull(language?.lowercase(), "en")
    return groupBy { it.uri?.let(::withoutLanguage) ?: it }
        .values
        .map { copies -> preferred.firstNotNullOfOrNull { lang -> copies.find { it.uri?.let(::languageOf) == lang } } ?: copies.first() }
}

/** DWD publishes each warning as /de/….DEU.xml and /en/….ENG.xml under the same identifier. */
private val LanguageCopy = Regex("/(de|en)/(.*)\\.(DEU|ENG)\\.xml$")

private fun withoutLanguage(uri: String) = LanguageCopy.replace(uri, "/$2.xml")

private fun languageOf(uri: String) = LanguageCopy.find(uri)?.groupValues?.get(1)
