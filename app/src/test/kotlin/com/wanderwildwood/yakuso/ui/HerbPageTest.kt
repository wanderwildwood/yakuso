package com.wanderwildwood.yakuso.ui

import com.wanderwildwood.yakuso.herbs.HerbBook
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HerbPageTest {
    private val herbs = HerbBook.parse(File("src/main/assets/herbs.json").readText())

    @Test fun cautionsComeAfterThePlantOnAnOrdinaryPage() {
        val chamomile = herbs.single { it.id == "chamomile" }
        assertEquals(
            listOf(Section.TRADITIONALLY, Section.PREPARE, Section.PLANT, Section.CAUTIONS, Section.SOURCES),
            sectionsOf(chamomile),
        )
    }

    @Test fun aSeriousHerbSaysSoAtTheTopButADontUsePageNeedsNoLine() {
        assertTrue(herbs.filter { it.serious && !it.dontUse }.all { hasCautionLine(it) })
        assertTrue(hasCautionLine(herbs.single { it.id == "st-johns-wort" }))
        assertFalse(hasCautionLine(herbs.single { it.id == "comfrey" }))
        assertFalse(herbs.filter { !it.serious }.any { hasCautionLine(it) })
    }

    @Test fun aDontUsePageOpensWithItAndHasNoPreparation() {
        val comfrey = herbs.single { it.id == "comfrey" }
        val sections = sectionsOf(comfrey)
        assertEquals(Section.DONT_TAKE, sections.first())
        assertEquals(Section.CAUTIONS, sections[1])
        assertFalse(Section.PREPARE in sections)
    }

    @Test fun temperatureIsFahrenheitThenCelsius() {
        val p = herbs.single { it.id == "chamomile" }.preparation!!
        assertEquals("212 °F (100 °C)", temperature("%1\$d °F (%2\$d °C)", p))
        assertEquals("5 to 10 minutes", minutes("%1\$d to %2\$d minutes", p))
    }

    @Test fun listsReadAsASentence() {
        assertEquals("", joinedList(emptyList(), "and"))
        assertEquals("tea", joinedList(listOf("tea"), "and"))
        assertEquals("a, b and c", joinedList(listOf("a", "b", "c"), "and"))
        assertEquals("Anxiety and colds.", sentence(joinedList(listOf("anxiety", "colds"), "and")))
    }
}
