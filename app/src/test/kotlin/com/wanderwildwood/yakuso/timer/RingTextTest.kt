package com.wanderwildwood.yakuso.timer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The ring's notification text: seconds under a minute, minutes from a minute up. */
@RunWith(RobolectricTestRunner::class)
class RingTextTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun text(seconds: Int) = Ringing.text(context, Brew("Chamomile", seconds))

    @Test fun underAMinuteIsSeconds() = assertEquals("Chamomile, 30 s", text(30))

    @Test fun aMinuteUpIsMinutes() {
        assertEquals("Chamomile, 1 min", text(60))
        assertEquals("Chamomile, 4 min", text(240))
        assertEquals("Chamomile, 4:30 min", text(270))
    }
}
