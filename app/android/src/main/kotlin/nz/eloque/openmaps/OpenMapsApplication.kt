package nz.eloque.openmaps

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nz.eloque.openmaps.core.model.LocationUpdates
import nz.eloque.openmaps.core.model.ServiceCatalog
import nz.eloque.openmaps.core.model.TripWatcher
import nz.eloque.openmaps.core.model.VoiceGuidance
import nz.eloque.openmaps.core.platform.Contacts
import nz.eloque.openmaps.core.platform.EventCalendar
import nz.eloque.openmaps.core.platform.Files
import nz.eloque.openmaps.core.platform.ReminderScheduler
import nz.eloque.openmaps.core.platform.Sharer
import nz.eloque.openmaps.core.platform.createHttpClient
import nz.eloque.openmaps.data.fuel.fuelModule
import nz.eloque.openmaps.data.fuel.fuelServiceSettings
import nz.eloque.openmaps.data.geocoding.geocodingModule
import nz.eloque.openmaps.data.geocoding.geocodingServiceSettings
import nz.eloque.openmaps.data.reviews.reviewsModule
import nz.eloque.openmaps.data.reviews.reviewsServiceSettings
import nz.eloque.openmaps.data.routing.routingModule
import nz.eloque.openmaps.data.routing.routingServiceSettings
import nz.eloque.openmaps.data.storage.storageModule
import nz.eloque.openmaps.data.transit.transitModule
import nz.eloque.openmaps.data.transit.transitServiceSettings
import nz.eloque.openmaps.data.weather.weatherModule
import nz.eloque.openmaps.data.weather.weatherServiceSettings
import nz.eloque.openmaps.navigation.AndroidVoiceGuidance
import nz.eloque.openmaps.navigation.NavigationService
import nz.eloque.openmaps.navigation.SpeedingAlert
import nz.eloque.openmaps.navigation.guidanceActive
import nz.eloque.openmaps.navigation.guidanceAnnouncements
import nz.eloque.openmaps.reminder.AndroidReminderScheduler
import nz.eloque.openmaps.tracks.RecordingService
import nz.eloque.openmaps.ui.tracks.TrackRecorder
import nz.eloque.openmaps.ui.uiModule
import nz.eloque.openmaps.watch.WorkManagerTripWatcher
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.util.Locale

class OpenMapsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val reminders = AndroidReminderScheduler(this)
        reminders.restore()
        startKoin {
            androidContext(this@OpenMapsApplication)
            modules(
                module {
                    single { createHttpClient(USER_AGENT) }
                    single<ReminderScheduler> { reminders }
                    single<LocationUpdates> { AndroidLocationUpdates(this@OpenMapsApplication) }
                    single<VoiceGuidance> {
                        AndroidVoiceGuidance(
                            this@OpenMapsApplication,
                            guidanceActive(get(), get()),
                            guidanceAnnouncements(get(), get()),
                            MainScope(),
                        )
                    }
                    single {
                        ServiceCatalog(
                            transitServiceSettings + routingServiceSettings + geocodingServiceSettings + fuelServiceSettings +
                                weatherServiceSettings + reviewsServiceSettings,
                        )
                    }
                    single { AndroidFiles(this@OpenMapsApplication) }
                    single<Files> { get<AndroidFiles>() }
                    single { AndroidContacts(this@OpenMapsApplication) }
                    single<Contacts> { get<AndroidContacts>() }
                    single<TripWatcher> { WorkManagerTripWatcher(this@OpenMapsApplication, get(), MainScope()) }
                    single<EventCalendar> {
                        EventCalendar { event ->
                            val insert =
                                Intent(Intent.ACTION_INSERT)
                                    .setData(CalendarContract.Events.CONTENT_URI)
                                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.start.toEpochMilliseconds())
                                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end.toEpochMilliseconds())
                                    .putExtra(CalendarContract.Events.TITLE, event.title)
                                    .putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
                                    .putExtra(CalendarContract.Events.DESCRIPTION, event.description)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                startActivity(insert)
                                true
                            } catch (_: ActivityNotFoundException) {
                                false
                            }
                        }
                    }
                    single<Sharer> {
                        Sharer { text ->
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                },
                transitModule(language = { Locale.getDefault().language }),
                routingModule(language = { Locale.getDefault().toLanguageTag() }),
                geocodingModule(language = { Locale.getDefault().language }),
                fuelModule,
                weatherModule(language = { Locale.getDefault().language }),
                reviewsModule,
                storageModule,
                uiModule,
            )
        }
        get<VoiceGuidance>()
        SpeedingAlert(get(), get(), MainScope())
        MainScope().launch {
            guidanceActive(get(), get()).filter { it }.collect { NavigationService.start(this@OpenMapsApplication) }
        }
        MainScope().launch {
            get<TrackRecorder>().recording.map { it != null }.distinctUntilChanged().filter { it }.collect {
                RecordingService.start(this@OpenMapsApplication)
            }
        }
    }

    private companion object {
        const val USER_AGENT = "OpenMaps/${BuildConfig.VERSION_NAME} (https://github.com/SeineEloquenz/openmaps)"
    }
}
