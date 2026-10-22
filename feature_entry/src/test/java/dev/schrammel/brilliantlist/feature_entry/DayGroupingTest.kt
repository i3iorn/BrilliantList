package dev.schrammel.brilliantlist.feature_entry

import dev.schrammel.brilliantlist.core_model.Entry
import dev.schrammel.brilliantlist.core_model.EntryType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.util.UUID

/**
 * The list is grouped by the day the entries were written.
 */
class DayGroupingTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun theFirstEntryOfADayIsMarked() {
        val day = 24L * 60 * 60 * 1000
        val entries = listOf(
            entry(at = 0L),
            entry(at = 60 * 60 * 1000L),
            entry(at = 2 * day),
            entry(at = 2 * day + 60 * 60 * 1000L)
        )

        assertEquals(
            listOf(true, false, true, false),
            dayStarts(entries, zone)
        )
    }

    @Test
    fun anEmptyListHasNoDays() {
        assertEquals(emptyList<Boolean>(), dayStarts(emptyList(), zone))
    }

    @Test
    fun aSingleEntryIsTheFirstOfItsDay() {
        assertEquals(listOf(true), dayStarts(listOf(entry(at = 0L)), zone))
    }

    @Test
    fun theOrderOfTheEntriesDecidesTheDays() {
        // The entries are in the order the user sees them, so the grouping
        // follows that order and not the time.
        val day = 24L * 60 * 60 * 1000
        val entries = listOf(
            entry(at = 2 * day),
            entry(at = 0L),
            entry(at = 3 * day)
        )

        assertEquals(
            listOf(true, true, true),
            dayStarts(entries, zone)
        )
    }

    private fun entry(at: Long) = Entry(
        id = UUID.randomUUID(),
        type = EntryType.TEXT,
        textContent = "an entry",
        createdAt = at,
        updatedAt = at,
        position = 0
    )
}
