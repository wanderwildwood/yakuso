package com.wanderwildwood.yakuso.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wanderwildwood.yakuso.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AboutWordsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theAboutSaysItIsNotMedicalAdvice() {
        assertEquals("Not medical advice", context.getString(R.string.about_safety_heading))
        assertTrue(context.getString(R.string.safety_disclaimer).contains("not medical advice"))
    }

    @Test
    fun theUsesHeadingIsAlwaysTraditionallyUsedFor() {
        assertEquals("Traditionally used for", context.getString(R.string.herb_traditionally))
    }

    @Test
    fun temperaturesAreFahrenheitFirst() {
        val f = context.getString(R.string.prep_temp)
        assertTrue(f.indexOf("°F") < f.indexOf("°C"))
    }

    @Test
    fun noStringPromisesACure() {
        val words = Regex("\\b(cure[sd]?|curing|treats?|treated)\\b", RegexOption.IGNORE_CASE)
        R.string::class.java.fields.forEach { field ->
            val text = context.getString(field.getInt(null))
            assertFalse("${field.name}: $text", words.containsMatchIn(text))
        }
    }
}
