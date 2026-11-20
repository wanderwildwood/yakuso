package com.wanderwildwood.yakuso.herbs

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
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
        assertEquals(44, herbs.size)
        assertEquals("Black tea", herbs.first().name)
        assertEquals("Yarrow", herbs.last().name)
        assertTrue(herbs.none { it.id == "oat-straw" })
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
                "st-johns-wort", "turmeric", "valerian", "yarrow",
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
        assertTrue(avoid.containsAll(listOf("passionflower", "feverfew", "sage", "st-johns-wort", "yarrow")))
        assertTrue(HerbBook.forNeed(herbs, "pregnancy").none { it.id in avoid })
    }

    @Test fun everyLineNamesASourceWithALicense() {
        val licenses = setOf("public-domain", "cc-by-3.0", "cc-by-4.0", "cc-by-sa-4.0", "own", "none")
        val book = JSONObject(File("src/main/assets/herbs.json").readText())
        val shared = book.getJSONObject("sharedSources")
        shared.keys().forEach { assertTrue("shared $it", shared.getJSONObject(it).getString("license") in licenses) }
        val herbsJson = book.getJSONArray("herbs")
        var lines = 0
        for (n in 0 until herbsJson.length()) {
            val h = herbsJson.getJSONObject(n)
            val id = h.getString("id")
            val sources = h.getJSONArray("sources")
            val ids = (0 until sources.length()).map { sources.getJSONObject(it) }.onEach {
                assertTrue("$id: ${it.optString("id")} license", it.optString("license") in licenses)
            }.map { it.getString("id") }.toSet() + shared.keys().asSequence().toSet()
            fun check(what: String, src: JSONArray?) {
                lines++
                assertTrue("$id: $what has no source", src != null && src.length() > 0)
                for (i in 0 until src!!.length()) assertTrue("$id: $what names ${src.getString(i)}", src.getString(i) in ids)
            }
            val cautions = h.getJSONArray("cautions")
            for (i in 0 until cautions.length()) check("caution $i", cautions.getJSONObject(i).optJSONArray("src"))
            val uses = h.getJSONArray("uses")
            for (i in 0 until uses.length()) check("use $i", uses.getJSONObject(i).optJSONArray("src"))
            mapOf(
                "research" to "researchSrc", "dontUseReason" to "dontUseReasonSrc", "taste" to "tasteSrc",
                "parts" to "partsSrc", "alsoCalled" to "alsoCalledSrc", "interactions" to "interactionsSrc",
            ).forEach { (field, srcField) ->
                val present = when (val v = h.opt(field)) {
                    is String -> v.isNotBlank()
                    is JSONArray -> v.length() > 0
                    else -> false
                }
                if (present) check(field, h.optJSONArray(srcField))
            }
            h.optJSONObject("preparation")?.let { check("preparation", it.optJSONArray("src")) }
        }
        assertTrue(lines > 400)
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

    @Test fun celsiusIsReadAsWrittenNotConverted() {
        val green = herbs.single { it.id == "green-tea" }.preparation!!
        assertEquals(175, green.tempF)
        assertEquals(80, green.tempC) // the kitchen pairing; celsius(175) would say 79
        herbs.mapNotNull { it.preparation }.forEach { p ->
            assertTrue("${p.tempF} F beside ${p.tempC} C", kotlin.math.abs(celsius(p.tempF) - p.tempC) <= 1)
        }
    }

    @Test fun celsiusRoundsFromFahrenheit() {
        assertEquals(100, celsius(212))
        assertEquals(79, celsius(175)) // 79.4: the plan's "175 °F (80 °C)" is a kitchen pairing, not a conversion
        assertEquals(0, celsius(32))
        assertEquals(93, celsius(200))
    }
}
