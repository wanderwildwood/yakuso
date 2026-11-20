package com.wanderwildwood.yakuso.herbs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The bundled herb book as the app reads it (org.json needs Robolectric on the JVM). */
@RunWith(RobolectricTestRunner::class)
class HerbBookTest {
    private val herbs = HerbBook.parse(File("src/main/assets/herbs.json").readText())

    @Test fun theBookHasTheStarterListAtoZ() {
        assertEquals(45, herbs.size)
        assertEquals("Black tea", herbs.first().name)
        assertEquals("Yarrow", herbs.last().name)
        assertEquals(HerbBook.sortedAtoZ(herbs.shuffled()).map { it.id }, herbs.map { it.id })
        assertEquals(herbs.size, herbs.map { it.id }.toSet().size)
    }

    @Test fun theDontUsePagesAreTheFour() {
        assertEquals(setOf("comfrey", "ephedra", "kava", "pennyroyal"), herbs.filter { it.dontUse }.map { it.id }.toSet())
        herbs.filter { it.dontUse }.forEach {
            assertNull("${it.id} has a preparation", it.preparation)
            assertFalse("${it.id} has no reason", it.dontUseReason.isNullOrBlank())
        }
    }

    @Test fun orderIgnoresCaseAndAccents() {
        val one = herbs.first()
        val mixed = listOf(
            one.copy(id = "z", name = "zest"), one.copy(id = "a", name = "Angelica"),
            one.copy(id = "e", name = "Échinacea"), one.copy(id = "b", name = "basil"),
        )
        assertEquals(listOf("Angelica", "basil", "Échinacea", "zest"), HerbBook.sortedAtoZ(mixed).map { it.name })
    }

    @Test fun everyHerbHasASourceAndACaution() {
        herbs.forEach {
            assertTrue("${it.id} has no source", it.sources.isNotEmpty())
            assertTrue("${it.id} has no caution", it.cautions.isNotEmpty())
            it.sources.forEach { s -> assertTrue(s.url.startsWith("https://")) }
        }
    }

    @Test fun comfreyIsADontUsePageWithNoPreparation() {
        val comfrey = herbs.single { it.id == "comfrey" }
        assertTrue(comfrey.dontUse)
        assertNull(comfrey.preparation)
        assertFalse(comfrey.dontUseReason.isNullOrBlank())
        assertTrue(comfrey.serious)
    }

    @Test fun onlyAvoidCautionsMarkTheRow() {
        val marked = herbs.filter { it.serious }.map { it.id }.toSet()
        assertEquals(
            setOf(
                "cinnamon", "comfrey", "elderberry", "elderflower", "ephedra", "fennel", "feverfew", "ginkgo",
                "ginseng", "hawthorn", "kava", "licorice", "passionflower", "pennyroyal", "red-clover", "sage",
                "st-johns-wort", "turmeric", "valerian",
            ),
            marked,
        )
    }

    @Test fun stJohnsWortListsItsInteractionsAndAvoidComesFirst() {
        val sjw = herbs.single { it.id == "st-johns-wort" }
        assertTrue(sjw.interactions.size >= 8)
        assertTrue(sjw.interactions.any { "warfarin" in it })
        val order = sjw.cautionsInOrder.map { it.severity }
        assertEquals(order.sortedBy { it.ordinal }, order)
        assertEquals(Severity.AVOID, order.first())
    }

    @Test fun needsComeFromTheUsesInFinderOrder() {
        herbs.forEach { h -> h.uses.flatMap { it.needs }.forEach { assertTrue("${h.id}: $it", it in NEEDS) } }
        val ginger = herbs.single { it.id == "ginger" }
        assertEquals(listOf("digestion", "colds", "nausea", "period-cramps", "pregnancy"), ginger.needs)
        assertEquals(listOf("ginger"), HerbBook.forNeed(herbs, "pregnancy").map { it.id })
        assertEquals(listOf("fennel", "lemon-balm", "marshmallow"), HerbBook.forNeed(herbs, "nursing").map { it.id })
        assertTrue(HerbBook.forNeed(herbs, "sleep").map { it.id }.containsAll(listOf("chamomile", "valerian")))
    }

    @Test fun aDontUsePageIsNeverInTheFinder() {
        val kava = herbs.single { it.id == "kava" }
        assertTrue(kava.needs.isEmpty())
        val tagged = kava.copy(uses = kava.uses.map { it.copy(needs = listOf("calm")) })
        assertTrue(tagged.needs.isEmpty())
    }

    @Test fun noHerbToAvoidInPregnancyIsListedForIt() {
        val avoid = herbs.filter { h ->
            h.cautions.any { it.severity == Severity.AVOID && "pregnan" in it.text.lowercase() }
        }.map { it.id }
        assertTrue(avoid.containsAll(listOf("passionflower", "feverfew", "sage", "st-johns-wort")))
        assertTrue(HerbBook.forNeed(herbs, "pregnancy").none { it.id in avoid })
    }

    @Test fun everyHerbIsStillMarkedDraft() {
        assertTrue(herbs.all { it.draft })
    }

    @Test fun searchFindsByNameLatinAndOtherNames() {
        assertEquals(listOf("st-johns-wort"), HerbBook.matching(herbs, "st johns").map { it.id })
        assertEquals(listOf("st-johns-wort"), HerbBook.matching(herbs, "Hypericum").map { it.id })
        assertEquals(listOf("chamomile"), HerbBook.matching(herbs, "german").map { it.id })
        assertEquals(herbs, HerbBook.matching(herbs, "  "))
        assertTrue(HerbBook.matching(herbs, "zzz").isEmpty())
    }

    @Test fun celsiusRoundsFromFahrenheit() {
        assertEquals(100, celsius(212))
        assertEquals(79, celsius(175)) // 79.4: the plan's "175 °F (80 °C)" is a kitchen pairing, not a conversion
        assertEquals(0, celsius(32))
        assertEquals(93, celsius(200))
    }
}
