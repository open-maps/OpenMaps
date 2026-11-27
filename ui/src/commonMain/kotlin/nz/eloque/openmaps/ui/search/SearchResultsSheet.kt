package nz.eloque.openmaps.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.Geocoder
import nz.eloque.openmaps.core.model.PlacesStore
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.core.model.SettingsStore
import nz.eloque.openmaps.map.MapController
import nz.eloque.openmaps.ui.components.LoadState
import nz.eloque.openmaps.ui.components.LoadStateContent
import nz.eloque.openmaps.ui.components.formatDistance
import nz.eloque.openmaps.ui.components.icon
import nz.eloque.openmaps.ui.components.loadOnce
import nz.eloque.openmaps.ui.navigation.SearchResultsKey
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.no_results
import nz.eloque.openmaps.ui.resources.search_edit
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Every result of a search as pins on the map and as a list, so they can be compared before one is opened. */
@Composable
fun SearchResultsSheet(
    key: SearchResultsKey,
    map: MapController,
    onOpen: (SearchResult) -> Unit,
    onEditQuery: () -> Unit,
) {
    val lastSearch = koinInject<LastSearch>()
    val geocoder = koinInject<Geocoder>()
    val places = koinInject<PlacesStore>()
    val unit by koinInject<SettingsStore>().distanceUnit.collectAsState()
    val scope = rememberCoroutineScope()
    val state by produceState(
        lastSearch.results.takeIf { lastSearch.query == key.query }?.let { LoadState.Loaded(it) } ?: LoadState.Loading,
        key.query,
    ) {
        if (value is LoadState.Loaded) return@produceState
        value = loadOnce { geocoder.search(key.query, map.userLocation ?: map.center) }
    }
    val results = (state as? LoadState.Loaded)?.value.orEmpty()
    val open: (SearchResult) -> Unit = { result ->
        scope.launch { places.addRecent(result) }
        onOpen(result)
    }

    DisposableEffect(results) {
        val pins = results.map { it.asPin() }
        val previousClick = map.onPlaceClick
        map.resultIcon = Icons.Default.Place
        map.results = pins
        map.onPlaceClick = { place -> results.getOrNull(pins.indexOf(place))?.let(open) ?: previousClick?.invoke(place) }
        onDispose {
            if (map.results === pins) map.results = emptyList()
            map.resultIcon = null
            map.onPlaceClick = previousClick
        }
    }
    var fitted by rememberSaveable(key.query) { mutableStateOf(false) }
    LaunchedEffect(results) {
        if (fitted || results.isEmpty()) return@LaunchedEffect
        fitted = true
        val origin = map.userLocation ?: map.center
        val nearest = results.minBy { it.location.distanceTo(origin) }.location
        val points = results.map { it.location }.filter { it.distanceTo(nearest) <= FIT_RADIUS_METERS }
        if (points.size == 1) map.show(points.single()) else map.fit(points)
    }

    val origin = map.userLocation ?: map.center
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onEditQuery).padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(key.query, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.search_edit))
            }
        }
        item {
            LoadStateContent(state, onRetry = {}, modifier = Modifier.fillMaxWidth()) {
                if (it.isEmpty()) {
                    Text(
                        stringResource(Res.string.no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        items(results) { result ->
            ListItem(
                headlineContent = { Text(result.name) },
                supportingContent = {
                    Text(listOfNotNull(formatDistance(result.location.distanceTo(origin), unit), result.area).joinToString(" · "))
                },
                leadingContent = { Icon(result.icon, contentDescription = null) },
                modifier = Modifier.clickable { open(result) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

private fun SearchResult.asPin(): SearchResult.Place =
    this as? SearchResult.Place ?: SearchResult.Place(id = null, name = name, location = location, area = area, category = null)

private const val FIT_RADIUS_METERS = 25_000.0
