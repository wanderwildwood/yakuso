package com.wanderwildwood.yakuso.ui

import com.wanderwildwood.yakuso.herbs.HerbBook
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EntriesTest {
    private val herbs = HerbBook.parse(File("src/main/assets/herbs.json").readText())

    @Test fun steepIsMinutesOrMinutesAndSeconds() {
        assertEquals(240, parseSteep("4"))
        assertEquals(150, parseSteep("2:30"))
        assertEquals(270, parseSteep(" 4.5 "))
        assertNull(parseSteep(""))
        assertNull(parseSteep("a while"))
        assertNull(parseSteep("0"))
        assertNull(parseSteep("2:75"))
    }

    @Test fun aDayIsShortThisYearAndCarriesItsYearBefore() {
        val today = LocalDate.of(2026, 11, 20)
        assertEquals("Fri, Nov 20", dayText(today.toEpochDay(), today))
        assertEquals("Nov 20, 2025", dayText(today.minusYears(1).toEpochDay(), today))
    }

    @Test fun theNameFieldOffersTheBookButNeverADontUsePage() {
        assertTrue(suggestions(herbs, "").isEmpty())
        assertEquals(listOf("chamomile"), suggestions(herbs, "chamo").map { it.id })
        // Once the words are an herb's name, nothing more is offered.
        assertTrue(suggestions(herbs, "Chamomile").isEmpty())
        assertTrue(suggestions(herbs, "comfrey").isEmpty())
        assertTrue(suggestions(herbs, "a").size <= 3)
        assertEquals("ginger", herbNamed(herbs, " ginger ")?.id)
        assertNull(herbNamed(herbs, "my own blend"))
    }

    @Test fun aDayNoCalendarHoldsShowsAsItsNumber() =
        assertEquals("1000000000000000", dayText(1_000_000_000_000_000L))

    @Test fun aCreativeCommonsSourceSaysWhoUnderWhatAndThatItWasReworded() =
        assertEquals(
            "Wikipedia contributors · CC BY-SA 4.0 · adapted, not quoted",
            sourceCredit("Wikipedia contributors", licenseName("cc-by-sa-4.0"), "adapted, not quoted"),
        )
}
