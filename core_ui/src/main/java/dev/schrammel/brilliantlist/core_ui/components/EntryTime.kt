package dev.schrammel.brilliantlist.core_ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.schrammel.brilliantlist.core_ui.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The time of an entry, in a form a person can read at a glance.
 *
 * "Today" and "Yesterday" are more useful than a date for a journal, the
 * exact date only matters for older entries.
 */
@Composable
fun formatEntryTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(timestamp).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val time = TIME_FORMAT.format(dateTime)
    return when (dateTime.toLocalDate()) {
        today -> stringResource(R.string.entry_time_today, time)
        today.minusDays(1) -> stringResource(R.string.entry_time_yesterday, time)
        else -> DATE_TIME_FORMAT.format(dateTime)
    }
}

/**
 * The day of a timestamp, in the time zone of the device.
 *
 * The list is grouped by it, so the day has to be the one the person sees on
 * their clock.
 */
fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/** The day a header of the list shows. */
@Composable
fun formatDayHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> stringResource(R.string.entry_day_today)
    today.minusDays(1) -> stringResource(R.string.entry_day_yesterday)
    else -> DATE_FORMAT.format(date)
}

/**
 * The formatters are created once.
 *
 * They are immutable and thread safe, and the list asks for the time of
 * every entry on every recomposition.
 */
private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private val DATE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
