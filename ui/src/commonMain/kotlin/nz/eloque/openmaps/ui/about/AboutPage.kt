package nz.eloque.openmaps.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.components.About
import nz.eloque.compose_kit.components.AboutLink
import nz.eloque.openmaps.ui.components.Page
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.about
import nz.eloque.openmaps.ui.resources.about_licence
import nz.eloque.openmaps.ui.resources.app_icon
import nz.eloque.openmaps.ui.resources.app_name
import nz.eloque.openmaps.ui.resources.data_sources
import nz.eloque.openmaps.ui.resources.privacy
import nz.eloque.openmaps.ui.resources.privacy_detail
import nz.eloque.openmaps.ui.resources.source_charging
import nz.eloque.openmaps.ui.resources.source_charging_detail
import nz.eloque.openmaps.ui.resources.source_code
import nz.eloque.openmaps.ui.resources.source_driving
import nz.eloque.openmaps.ui.resources.source_driving_detail
import nz.eloque.openmaps.ui.resources.source_fix_map
import nz.eloque.openmaps.ui.resources.source_flights
import nz.eloque.openmaps.ui.resources.source_flights_detail
import nz.eloque.openmaps.ui.resources.source_fuel
import nz.eloque.openmaps.ui.resources.source_fuel_detail
import nz.eloque.openmaps.ui.resources.source_layers
import nz.eloque.openmaps.ui.resources.source_layers_detail
import nz.eloque.openmaps.ui.resources.source_map
import nz.eloque.openmaps.ui.resources.source_map_detail
import nz.eloque.openmaps.ui.resources.source_media
import nz.eloque.openmaps.ui.resources.source_media_detail
import nz.eloque.openmaps.ui.resources.source_nearby
import nz.eloque.openmaps.ui.resources.source_nearby_detail
import nz.eloque.openmaps.ui.resources.source_places
import nz.eloque.openmaps.ui.resources.source_places_detail
import nz.eloque.openmaps.ui.resources.source_reviews
import nz.eloque.openmaps.ui.resources.source_reviews_detail
import nz.eloque.openmaps.ui.resources.source_roadworks
import nz.eloque.openmaps.ui.resources.source_roadworks_detail
import nz.eloque.openmaps.ui.resources.source_tiles
import nz.eloque.openmaps.ui.resources.source_tiles_detail
import nz.eloque.openmaps.ui.resources.source_transit
import nz.eloque.openmaps.ui.resources.source_transit_detail
import nz.eloque.openmaps.ui.resources.source_walking
import nz.eloque.openmaps.ui.resources.source_walking_detail
import nz.eloque.openmaps.ui.resources.source_weather
import nz.eloque.openmaps.ui.resources.source_weather_detail
import nz.eloque.openmaps.ui.resources.the_licence
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AboutPage(
    version: String,
    onBack: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenPrivacy: () -> Unit,
) = Page(stringResource(Res.string.about), onBack) {
    About(
        appName = stringResource(Res.string.app_name),
        icon = painterResource(Res.drawable.app_icon),
        tagline = stringResource(Res.string.about_licence),
        taglineIcon = Icons.Default.Code,
        version = version,
        links =
            listOf(
                AboutLink.Uri(Icons.Default.Code, stringResource(Res.string.source_code), REPOSITORY),
                AboutLink.Uri(Icons.Default.Gavel, stringResource(Res.string.the_licence), "$REPOSITORY/blob/main/LICENSE"),
                AboutLink.Action(Icons.Default.Source, stringResource(Res.string.data_sources), onOpenSources),
                AboutLink.Action(Icons.Default.PrivacyTip, stringResource(Res.string.privacy), onOpenPrivacy),
                AboutLink.Uri(
                    Icons.Default.EditLocationAlt,
                    stringResource(Res.string.source_fix_map),
                    "https://www.openstreetmap.org/fixthemap",
                ),
            ),
        modifier = Modifier.navigationBarsPadding(),
    )
}

/** Every service and dataset the app shows or asks, each linking to its terms. */
@Composable
fun DataSourcesPage(onBack: () -> Unit) =
    Page(stringResource(Res.string.data_sources), onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            SourceRow(Res.string.source_map, Res.string.source_map_detail, "https://www.openstreetmap.org/copyright")
            SourceRow(Res.string.source_tiles, Res.string.source_tiles_detail, "https://openfreemap.org")
            SourceRow(Res.string.source_transit, Res.string.source_transit_detail, "https://transitous.org/sources/")
            SourceRow(Res.string.source_driving, Res.string.source_driving_detail, "https://github.com/valhalla/valhalla")
            SourceRow(Res.string.source_walking, Res.string.source_walking_detail, "https://fossgis.de")
            SourceRow(Res.string.source_roadworks, Res.string.source_roadworks_detail, "https://ori.eloque.nz/v1/sources.json")
            SourceRow(Res.string.source_places, Res.string.source_places_detail, "https://nominatim.org")
            SourceRow(Res.string.source_nearby, Res.string.source_nearby_detail, "https://photon.komoot.io")
            SourceRow(Res.string.source_media, Res.string.source_media_detail, "https://commons.wikimedia.org")
            SourceRow(Res.string.source_reviews, Res.string.source_reviews_detail, "https://mangrove.reviews")
            SourceRow(Res.string.source_weather, Res.string.source_weather_detail, "https://open-meteo.com")
            SourceRow(Res.string.source_flights, Res.string.source_flights_detail, "https://www.adsb.lol")
            SourceRow(Res.string.source_layers, Res.string.source_layers_detail, "https://www.openseamap.org")
            SourceRow(Res.string.source_charging, Res.string.source_charging_detail, "https://openchargemap.org")
            SourceRow(Res.string.source_fuel, Res.string.source_fuel_detail, "https://www.tankpuls.de")
        }
    }

/** What leaves the device, and where it goes. */
@Composable
fun PrivacyPage(onBack: () -> Unit) =
    Page(stringResource(Res.string.privacy), onBack) {
        Text(
            stringResource(Res.string.privacy_detail),
            modifier = Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
        )
    }

@Composable
private fun SourceRow(
    title: StringResource,
    detail: StringResource,
    url: String,
) {
    val uriHandler = LocalUriHandler.current
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(detail)) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { uriHandler.openUri(url) },
    )
}

private const val REPOSITORY = "https://github.com/SeineEloquenz/OpenMaps"
