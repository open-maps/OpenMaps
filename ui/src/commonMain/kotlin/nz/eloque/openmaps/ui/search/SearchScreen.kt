package nz.eloque.openmaps.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.eloque.compose_kit.dialog.FullscreenDialog
import nz.eloque.openmaps.core.model.Aircraft
import nz.eloque.openmaps.core.model.FavouriteLabel
import nz.eloque.openmaps.core.model.LatLng
import nz.eloque.openmaps.core.model.PlaceCategory
import nz.eloque.openmaps.core.model.SearchResult
import nz.eloque.openmaps.ui.category.categoriesMatching
import nz.eloque.openmaps.ui.category.icon
import nz.eloque.openmaps.ui.category.label
import nz.eloque.openmaps.ui.components.LoadStateContent
import nz.eloque.openmaps.ui.components.icon
import nz.eloque.openmaps.ui.flights.label
import nz.eloque.openmaps.ui.resources.Res
import nz.eloque.openmaps.ui.resources.aircraft_on_ground
import nz.eloque.openmaps.ui.resources.aircraft_route
import nz.eloque.openmaps.ui.resources.back
import nz.eloque.openmaps.ui.resources.category_nearby
import nz.eloque.openmaps.ui.resources.clear
import nz.eloque.openmaps.ui.resources.contact_address
import nz.eloque.openmaps.ui.resources.favourites
import nz.eloque.openmaps.ui.resources.flight_in_air
import nz.eloque.openmaps.ui.resources.home
import nz.eloque.openmaps.ui.resources.no_results
import nz.eloque.openmaps.ui.resources.recent
import nz.eloque.openmaps.ui.resources.search_placeholder
import nz.eloque.openmaps.ui.resources.work
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SearchScreen(
    near: LatLng?,
    stopsFirst: Boolean,
    onDismiss: () -> Unit,
    onSelect: (SearchResult) -> Unit,
    onCategory: ((PlaceCategory) -> Unit)? = null,
    initialQuery: String? = null,
    onFlight: ((Aircraft) -> Unit)? = null,
) {
    val viewModel = koinViewModel<SearchViewModel>()
    LaunchedEffect(initialQuery) { initialQuery?.let(viewModel::setQuery) }
    viewModel.near = near
    viewModel.stopsFirst = stopsFirst
    viewModel.findsFlights = onFlight != null
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val flights by viewModel.flights.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val select: (SearchResult) -> Unit = { result ->
        viewModel.remember(result)
        onSelect(result)
    }

    FullscreenDialog(onDismiss = onDismiss, contentAlignment = Alignment.TopStart) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                    TextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text(stringResource(Res.string.search_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.clear))
                        }
                    }
                }
                HorizontalDivider()
                if (onCategory != null) {
                    categoriesMatching(query).take(MAX_CATEGORY_ROWS).forEach { category ->
                        ResultRow(
                            headline = stringResource(Res.string.category_nearby, stringResource(category.label)),
                            supporting = null,
                            icon = category.icon,
                            onClick = { onCategory(category) },
                        )
                    }
                }
                if (onFlight != null) {
                    flights.forEach { flight ->
                        ResultRow(
                            headline = listOfNotNull(flight.aircraft.callsign, flight.info.airline).joinToString(" · "),
                            supporting = flight.summary(),
                            icon = Icons.Default.Flight,
                            onClick = { onFlight(flight.aircraft) },
                        )
                    }
                }
                when (val state = results) {
                    null -> SuggestionList(suggestions, select, viewModel::searchContact)
                    else ->
                        LoadStateContent(
                            state,
                            onRetry = { viewModel.setQuery(query) },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { SearchResults(it, select) }
                }
            }
        }
    }
}

private const val MAX_CATEGORY_ROWS = 2

@Composable
private fun SuggestionList(
    suggestions: Suggestions,
    onSelect: (SearchResult) -> Unit,
    onContacts: () -> Unit,
) {
    LazyColumn {
        item {
            ResultRow(stringResource(Res.string.contact_address), null, Icons.Default.Contacts, onContacts)
        }
        section(Res.string.favourites, suggestions.favourites.isNotEmpty())
        items(suggestions.favourites, key = { "favourite-${it.id}" }) { favourite ->
            val label = favourite.label.titleRes
            ResultRow(
                headline = label?.let { stringResource(it) } ?: favourite.displayName,
                supporting = if (label != null) favourite.displayName else favourite.place.area,
                icon = favourite.label.icon,
                onClick = { onSelect(favourite.place) },
            )
        }
        section(Res.string.recent, suggestions.recents.isNotEmpty())
        items(suggestions.recents) { recent ->
            ResultRow(recent.name, recent.area, Icons.Default.History) { onSelect(recent) }
        }
    }
}

private fun LazyListScope.section(
    title: StringResource,
    visible: Boolean,
) {
    if (!visible) return
    item {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

val FavouriteLabel.titleRes: StringResource?
    get() =
        when (this) {
            FavouriteLabel.HOME -> Res.string.home
            FavouriteLabel.WORK -> Res.string.work
            FavouriteLabel.OTHER -> null
        }

@Composable
private fun SearchResults(
    results: List<SearchResult>,
    onSelect: (SearchResult) -> Unit,
) {
    if (results.isEmpty()) {
        Text(
            stringResource(Res.string.no_results),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        )
        return
    }
    LazyColumn {
        items(results) { result ->
            ResultRow(result.name, result.area, result.icon) { onSelect(result) }
        }
    }
}

@Composable
private fun ResultRow(
    headline: String,
    supporting: String?,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun FlightResult.summary(): String {
    val origin = info.origin
    val destination = info.destination
    return when {
        origin != null && destination != null -> stringResource(Res.string.aircraft_route, origin.label, destination.label)
        aircraft.onGround -> stringResource(Res.string.aircraft_on_ground)
        else -> stringResource(Res.string.flight_in_air)
    }
}
