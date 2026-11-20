package com.wanderwildwood.yakuso.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.yakuso.R
import com.wanderwildwood.yakuso.herbs.Caution
import com.wanderwildwood.yakuso.herbs.Herb
import com.wanderwildwood.yakuso.herbs.Preparation
import com.wanderwildwood.yakuso.herbs.Severity

/**
 * One herb. The sections always come in the same order, and cautions come first: who should not
 * take it is read before what it is used for. A "don't use" page opens with that and its reason,
 * and has no preparation.
 */
@Composable
fun HerbScreen(herb: Herb, onBack: () -> Unit) {
    val context = LocalContext.current
    val noBrowser = stringResource(R.string.about_no_browser_address, "@@")
    val words = HerbWords(
        draft = stringResource(R.string.herb_draft),
        dontTake = stringResource(R.string.herb_dont_take),
        cautions = stringResource(R.string.herb_cautions),
        interactsWith = stringResource(R.string.herb_interacts_with),
        askPharmacist = stringResource(R.string.herb_ask_pharmacist),
        avoid = stringResource(R.string.severity_avoid),
        caution = stringResource(R.string.severity_caution),
        note = stringResource(R.string.severity_note),
        traditionally = stringResource(R.string.herb_traditionally),
        research = stringResource(R.string.herb_research),
        prepare = stringResource(R.string.herb_prepare),
        infusion = stringResource(R.string.prep_infusion),
        decoction = stringResource(R.string.prep_decoction),
        water = stringResource(R.string.prep_water),
        steep = stringResource(R.string.prep_steep),
        simmer = stringResource(R.string.prep_simmer),
        aboutPlant = stringResource(R.string.herb_about_plant),
        latin = stringResource(R.string.herb_latin),
        parts = stringResource(R.string.herb_parts),
        taste = stringResource(R.string.herb_taste),
        alsoCalled = stringResource(R.string.herb_also_called),
        sources = stringResource(R.string.herb_sources),
        and = stringResource(R.string.list_and),
        tempFormat = stringResource(R.string.prep_temp),
        minutesFormat = stringResource(R.string.prep_minutes),
    )

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Bar(herb.name, onBack = onBack)
        Box(Modifier.weight(1f)) {
            SelectionContainer(Modifier.textActions()) {
                PagedColumn(
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                    showCutRow = true,
                ) {
                    herbPage(herb, words) { url ->
                        openLink(context, url, noBrowser.replace("@@", url)) { FootNotice.say(context, it) }
                    }
                }
            }
        }
    }
}

/** The page's words, read once so the list's items stay plain functions. */
internal data class HerbWords(
    val draft: String, val dontTake: String, val cautions: String, val interactsWith: String,
    val askPharmacist: String, val avoid: String, val caution: String, val note: String,
    val traditionally: String, val research: String, val prepare: String, val infusion: String,
    val decoction: String, val water: String, val steep: String, val simmer: String,
    val aboutPlant: String, val latin: String, val parts: String, val taste: String,
    val alsoCalled: String, val sources: String, val and: String,
    val tempFormat: String, val minutesFormat: String,
)

/** The sections, in their fixed order. */
internal enum class Section { DONT_TAKE, CAUTIONS, TRADITIONALLY, PREPARE, PLANT, SOURCES }

/** Which sections a herb's page has, in the order they are shown. */
internal fun sectionsOf(herb: Herb): List<Section> = buildList {
    if (herb.dontUse) add(Section.DONT_TAKE)
    add(Section.CAUTIONS)
    if (herb.uses.isNotEmpty() || herb.research != null) add(Section.TRADITIONALLY)
    if (!herb.dontUse && herb.preparation != null) add(Section.PREPARE)
    add(Section.PLANT)
    add(Section.SOURCES)
}

/** "a, b and c". */
internal fun joinedList(items: List<String>, and: String): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " $and " + items.last()
}

/** A list as a sentence: capital first letter, full stop at the end. */
internal fun sentence(text: String): String =
    text.replaceFirstChar { it.uppercase(java.util.Locale.US) }.let { if (it.endsWith(".")) it else "$it." }

/** "212 °F (100 °C)": Fahrenheit first, from a format with two %d. */
internal fun temperature(format: String, p: Preparation): String =
    String.format(java.util.Locale.US, format, p.tempF, p.tempC)

internal fun minutes(format: String, p: Preparation): String =
    String.format(java.util.Locale.US, format, p.steepMin, p.steepMax)

private fun LazyListScope.herbPage(herb: Herb, w: HerbWords, onLink: (String) -> Unit) {
    if (herb.draft) item(key = "draft") { Small(w.draft, Modifier.padding(bottom = 8.dp)) }

    sectionsOf(herb).forEach { section ->
        when (section) {
            Section.DONT_TAKE -> {
                item(key = "dont") { Heading(w.dontTake) }
                herb.dontUseReason?.let { reason -> item(key = "dont-why") { Body(reason) } }
            }

            Section.CAUTIONS -> {
                item(key = "cautions") { Heading(w.cautions) }
                herb.cautionsInOrder.forEachIndexed { i, c ->
                    item(key = "c$i") { CautionLine(c, w) }
                }
                if (herb.interactions.isNotEmpty()) {
                    item(key = "interacts") {
                        Body(w.interactsWith + " " + joinedList(herb.interactions, w.and) + ".")
                    }
                    item(key = "pharmacist") { Body(w.askPharmacist) }
                }
            }

            Section.TRADITIONALLY -> {
                item(key = "trad") { Heading(w.traditionally) }
                if (herb.uses.isNotEmpty()) {
                    item(key = "trad-list") {
                        Body(sentence(joinedList(herb.uses.map { it.forWhat }, w.and)))
                    }
                }
                herb.research?.let { r ->
                    item(key = "research") { Body(w.research + " " + r) }
                }
            }

            Section.PREPARE -> {
                val p = herb.preparation!!
                item(key = "prep") { Heading(w.prepare) }
                item(key = "prep-method") {
                    Body(if (p.method == "decoction") w.decoction else w.infusion)
                }
                item(key = "prep-amount") { Body(p.amount) }
                item(key = "prep-water") { Body(w.water + " " + temperature(w.tempFormat, p)) }
                item(key = "prep-steep") {
                    Body((if (p.method == "decoction") w.simmer else w.steep) + " " + minutes(w.minutesFormat, p))
                }
                p.note?.let { n -> item(key = "prep-note") { Body(n) } }
            }

            Section.PLANT -> {
                item(key = "plant") { Heading(w.aboutPlant) }
                if (herb.latin.isNotBlank()) item(key = "latin") { Body(w.latin + " " + herb.latin) }
                herb.parts?.let { item(key = "parts") { Body(w.parts + " " + it) } }
                herb.taste?.let { item(key = "taste") { Body(w.taste + " " + it) } }
                if (herb.alsoCalled.isNotEmpty()) {
                    item(key = "also") { Body(w.alsoCalled + " " + herb.alsoCalled.joinToString(", ")) }
                }
            }

            Section.SOURCES -> {
                item(key = "sources") { Heading(w.sources) }
                herb.sources.forEachIndexed { i, s ->
                    item(key = "s$i") {
                        Column(
                            Modifier.fillMaxWidth().clickable { onLink(s.url) }.padding(vertical = 6.dp),
                        ) {
                            TextMMD(s.title, style = MaterialTheme.typography.bodyMedium)
                            TextMMD(s.url.removePrefix("https://"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    TextMMD(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun Body(text: String, bold: Boolean = false) {
    TextMMD(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun Small(text: String, modifier: Modifier = Modifier) {
    TextMMD(text, style = MaterialTheme.typography.bodySmall, modifier = modifier.fillMaxWidth())
}

/** "Avoid: ..." in bold; a caution and a note in plain type, each with its word first. */
@Composable
private fun CautionLine(c: Caution, w: HerbWords) {
    val label = when (c.severity) {
        Severity.AVOID -> w.avoid
        Severity.CAUTION -> w.caution
        Severity.NOTE -> w.note
    }
    Body("$label ${c.text}", bold = c.severity == Severity.AVOID)
}
