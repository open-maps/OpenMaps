package nz.eloque.openmaps.core.platform

import kotlin.time.Instant

data class CalendarEvent(
    val title: String,
    val start: Instant,
    val end: Instant,
    val location: String?,
    val description: String,
)

/** Hands an event to the user's calendar app, which asks before saving it. */
fun interface EventCalendar {
    /** False when no calendar app is installed. */
    fun add(event: CalendarEvent): Boolean
}
