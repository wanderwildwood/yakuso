package com.wanderwildwood.yakuso.herbs

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt

/** How much a caution weighs. Only [AVOID] puts the mark on a row of the list. */
enum class Severity { AVOID, CAUTION, NOTE }

data class Caution(val severity: Severity, val text: String)

/** One thing an herb is traditionally used for, and what the research says about it. */
data class Use(val forWhat: String, val evidence: String)

data class Preparation(
    /** "infusion" or "decoction". */
    val method: String,
    val amount: String,
    val tempF: Int,
    val steepMin: Int,
    val steepMax: Int,
    val note: String?,
) {
    /** Celsius from the Fahrenheit the book is written in, rounded to a whole degree. */
    val tempC: Int get() = celsius(tempF)
}

data class Source(val title: String, val url: String, val retrieved: String?)

data class Herb(
    val id: String,
    val name: String,
    val latin: String,
    val alsoCalled: List<String>,
    /** Text not yet read and approved; the page says so. */
    val draft: Boolean,
    /** A "don't use" page: it opens with the reason and has no preparation. */
    val dontUse: Boolean,
    val dontUseReason: String?,
    val parts: String?,
    val taste: String?,
    val cautions: List<Caution>,
    val interactions: List<String>,
    val uses: List<Use>,
    val research: String?,
    val preparation: Preparation?,
    val sources: List<Source>,
) {
    /** A serious caution: the row in the list carries the mark. */
    val serious: Boolean get() = dontUse || cautions.any { it.severity == Severity.AVOID }

    /** Cautions in the order they are read: avoid, then caution, then note; the book's order within each. */
    val cautionsInOrder: List<Caution> get() = cautions.sortedBy { it.severity.ordinal }
}

fun celsius(fahrenheit: Int): Int = ((fahrenheit - 32) * 5 / 9.0).roundToInt()

/**
 * The herb book, bundled as assets/herbs.json and read field by field with org.json, so there is
 * nothing for R8 to strip out from under it.
 */
object HerbBook {
    const val ASSET = "herbs.json"

    @Volatile private var cached: List<Herb>? = null

    fun get(assets: AssetManager): List<Herb> =
        cached ?: parse(assets.open(ASSET).bufferedReader().use { it.readText() }).also { cached = it }

    /** Every herb, A to Z by common name (case and accents ignored). */
    fun parse(json: String): List<Herb> {
        val herbs = JSONObject(json).getJSONArray("herbs")
        return sortedAtoZ((0 until herbs.length()).map { herb(herbs.getJSONObject(it)) })
    }

    fun sortedAtoZ(herbs: List<Herb>): List<Herb> {
        val collator = Collator.getInstance(Locale.US).apply { strength = Collator.PRIMARY }
        return herbs.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    /** The herbs whose name, Latin name or other names hold [query]; all of them for a blank one. */
    fun matching(herbs: List<Herb>, query: String): List<Herb> {
        val q = fold(query.trim())
        if (q.isEmpty()) return herbs
        return herbs.filter { h -> (listOf(h.name, h.latin) + h.alsoCalled).any { q in fold(it) } }
    }

    /** Lower case, with the apostrophes and dots a person may or may not type ("st johns"). */
    private fun fold(s: String): String =
        s.lowercase(Locale.US).replace(Regex("[’'.]"), "")

    private fun herb(o: JSONObject) = Herb(
        id = o.getString("id"),
        name = o.getString("name"),
        latin = o.optString("latin"),
        alsoCalled = o.optJSONArray("alsoCalled").strings(),
        draft = o.optBoolean("draft", true),
        dontUse = o.optBoolean("dontUse", false),
        dontUseReason = o.text("dontUseReason"),
        parts = o.text("parts"),
        taste = o.text("taste"),
        cautions = o.optJSONArray("cautions").objects().map {
            Caution(Severity.valueOf(it.getString("severity").uppercase(Locale.US)), it.getString("text"))
        },
        interactions = o.optJSONArray("interactions").strings(),
        uses = o.optJSONArray("uses").objects().map { Use(it.getString("for"), it.optString("evidence")) },
        research = o.text("research"),
        preparation = o.optJSONObject("preparation")?.let {
            Preparation(
                method = it.getString("method"),
                amount = it.getString("amount"),
                tempF = it.getInt("tempF"),
                steepMin = it.getInt("steepMin"),
                steepMax = it.getInt("steepMax"),
                note = it.text("note"),
            )
        },
        sources = o.optJSONArray("sources").objects().map {
            Source(it.getString("title"), it.getString("url"), it.text("retrieved"))
        },
    )

    /** A string field, or null when it is missing, JSON null or blank. */
    private fun JSONObject.text(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
}
