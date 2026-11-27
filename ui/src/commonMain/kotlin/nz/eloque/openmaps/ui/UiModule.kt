package nz.eloque.openmaps.ui

import nz.eloque.openmaps.core.model.TripGuide
import nz.eloque.openmaps.ui.category.CategoryViewModel
import nz.eloque.openmaps.ui.components.FlightTracker
import nz.eloque.openmaps.ui.directions.DirectionsViewModel
import nz.eloque.openmaps.ui.directions.ItineraryCache
import nz.eloque.openmaps.ui.directions.TripViewModel
import nz.eloque.openmaps.ui.guidance.ActiveTrip
import nz.eloque.openmaps.ui.guidance.NavigationViewModel
import nz.eloque.openmaps.ui.guidance.TransitGuidanceViewModel
import nz.eloque.openmaps.ui.guidance.TransitTripGuide
import nz.eloque.openmaps.ui.place.PlaceViewModel
import nz.eloque.openmaps.ui.search.SearchViewModel
import nz.eloque.openmaps.ui.sharing.SharingViewModel
import nz.eloque.openmaps.ui.stop.StopViewModel
import nz.eloque.openmaps.ui.tracks.TrackRecorder
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val uiModule =
    module {
        viewModelOf(::SearchViewModel)
        viewModel { params -> StopViewModel(get(), params.get()) }
        viewModel { params ->
            PlaceViewModel(get(), get(), get(), getOrNull(), params.getOrNull(), params.get(), params.get(), params.get(), params.get())
        }
        single { ItineraryCache() }
        single { FlightTracker() }
        single { TrackRecorder(get(), get()) }
        viewModel { params -> DirectionsViewModel(get(), get(), get(), get(), params.get()) }
        single { ActiveTrip() }
        viewModel { params -> NavigationViewModel(get(), get(), get(), get(), params.get()) }
        single<TripGuide> { TransitTripGuide(get(), get()) }
        viewModel { params -> TransitGuidanceViewModel(get(), get(), get(), params.get()) }
        viewModel { params -> TripViewModel(get(), params.get()) }
        viewModel { params -> CategoryViewModel(get(), get(), params.get()) }
        viewModel { SharingViewModel(get()) }
    }
