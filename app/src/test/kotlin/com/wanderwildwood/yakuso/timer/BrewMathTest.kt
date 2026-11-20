package com.wanderwildwood.yakuso.timer

import com.wanderwildwood.yakuso.herbs.HerbBook
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrewMathTest {

    /** Every moment the clock is redrawn, from [startMs] left down to the end. */
    private fun redraws(startMs: Long): List<Long> {
        val at = mutableListOf<Long>()
        var left = startMs
        while (left > 0) {
            at += left
            left -= BrewMath.nextRedrawMs(left)
        }
        return at
    }

    @Test fun theClockMovesEveryTenSecondsThenEverySecond() {
        val at = redraws(4 * 60_000L)
        val gaps = at.zipWithNext { a, b -> a - b }
        // Above the last ten seconds, ten seconds apart; in them, one.
        at.zipWithNext().forEach { (a, b) ->
            val gap = a - b
            if (a > 10_000) assertEquals("at $a ms left", 10_000L, gap) else assertEquals("at $a ms left", 1_000L, gap)
        }
        // 4:00, 3:50 ... 0:20 is 23 draws; 0:10 ... 0:01 is ten more.
        assertEquals(33, at.size)
        assertEquals(gaps.size, at.size - 1)
        assertEquals(10, at.count { it <= 10_000 })
    }

    @Test fun aStartBetweenMarksWaitsOnlyToTheNextMark() {
        // 2:35 left: shows 2:40 until 2:30 is reached, five seconds on.
        assertEquals(160L, BrewMath.shownSeconds(155_000))
        assertEquals(5_000L, BrewMath.nextRedrawMs(155_000))
        // 10.4 s left: still the coarse step, shown as 0:20 until 0:10.
        assertEquals(20L, BrewMath.shownSeconds(10_400))
        assertEquals(400L, BrewMath.nextRedrawMs(10_400))
    }

    @Test fun theClockNeverSaysLessIsLeftThanIs() {
        var left = 4 * 60_000L
        while (left > 0) {
            assertTrue("at $left ms", BrewMath.shownSeconds(left) * 1000 >= left)
            left -= 137
        }
        assertEquals(0L, BrewMath.shownSeconds(0))
        assertEquals(0L, BrewMath.shownSeconds(-5))
        assertEquals(1L, BrewMath.shownSeconds(1))
        assertEquals(Long.MAX_VALUE, BrewMath.nextRedrawMs(0))
    }

    @Test fun clockAndMinutes() {
        assertEquals("4:00", BrewMath.clock(240))
        assertEquals("0:09", BrewMath.clock(9))
        assertEquals("20:00", BrewMath.clock(1200))
        assertEquals("4", BrewMath.minutes(240))
        assertEquals("2:30", BrewMath.minutes(150))
    }

    @Test fun thePresetsAreThePlans() {
        val p = BrewMath.BUILT_IN.associateBy { it.key }
        assertEquals(listOf("green", "black", "herbal", "decoction"), BrewMath.BUILT_IN.map { it.key })
        assertEquals(120 to 180, p.getValue("green").minSeconds to p.getValue("green").maxSeconds)
        assertEquals(240 to 240, p.getValue("black").minSeconds to p.getValue("black").maxSeconds)
        assertEquals(300 to 600, p.getValue("herbal").minSeconds to p.getValue("herbal").maxSeconds)
        assertEquals(900 to 1200, p.getValue("decoction").minSeconds to p.getValue("decoction").maxSeconds)
        assertNull(p.getValue("herbal").temp)
        assertNull(p.getValue("decoction").temp)
    }

    @Test fun temperaturesArePairsAsWrittenNotConversions() {
        val p = BrewMath.BUILT_IN.associateBy { it.key }
        // 175 °F converts to 79.4 °C; the plan pairs it with 80, and 80 is what is stored.
        assertEquals(Temp(175, 80), p.getValue("green").temp)
        assertEquals(Temp(212, 100), p.getValue("black").temp)
    }

    @Test fun minusAndPlusStayInBounds() {
        assertEquals(210, BrewMath.adjust(240, -30))
        assertEquals(270, BrewMath.adjust(240, 30))
        assertEquals(BrewMath.MIN_SECONDS, BrewMath.adjust(30, -30))
        assertEquals(BrewMath.MAX_SECONDS, BrewMath.adjust(BrewMath.MAX_SECONDS, 30))
    }

    @Test fun aBrewKnowsWhereItStands() {
        val idle = Brew("Black tea", 240)
        assertFalse(idle.running)
        assertEquals(240_000L, idle.remainingMs(0))
        val running = idle.copy(endAt = 1_000_000)
        assertTrue(running.running)
        assertEquals(60_000L, running.remainingMs(940_000))
        assertEquals(0L, running.remainingMs(2_000_000))
        val done = running.copy(finishedAt = 1_000_100)
        assertTrue(done.finished)
        assertFalse(done.running)
    }

    @Test fun aHerbsPageOpensTheTimerWithItsPreparation() {
        val herbs = HerbBook.parse(File("src/main/assets/herbs.json").readText())
        val green = BrewMath.fromHerb(herbs.single { it.id == "green-tea" })!!
        assertEquals("Green tea", green.label)
        assertEquals(Temp(175, 80), green.temp)
        assertEquals("green-tea", green.herbId)
        val chamomile = herbs.single { it.id == "chamomile" }
        assertEquals(chamomile.preparation!!.steepMin * 60, BrewMath.fromHerb(chamomile)!!.seconds)
        // A "don't use" page never opens the timer.
        herbs.filter { it.dontUse }.forEach { assertNull(it.id, BrewMath.fromHerb(it)) }
    }
}
