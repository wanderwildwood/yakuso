package com.wanderwildwood.yakuso.data

import androidx.room.withTransaction
import com.wanderwildwood.yakuso.timer.BrewMath
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The journal, the cupboard and the person's own timer settings as one JSON file: plain enough
 * to read without this app, and read back in by it.
 *
 * Reading a copy back in only ever adds. A row whose uuid is already here is left exactly as it
 * is, so an old copy read onto a phone with newer entries keeps both, and reading the same file
 * twice changes nothing the second time. (Mushroom Journal's import, the same rule.)
 */
object Backup {
    const val KIND = "herbal-backup"
    const val FORMAT = 1

    data class Contents(
        val journal: List<JournalEntry>,
        val cupboard: List<CupboardItem>,
        val presets: List<OwnPreset>,
    )

    /** What a read did: how many came in and how many were already here. */
    data class Result(val added: Int, val alreadyHere: Int)

    /** Not a backup this app can read; nothing was changed. */
    class NotABackup(message: String) : Exception(message)

    fun encode(c: Contents, exportedAt: Long): String = JSONObject()
        .put("kind", KIND)
        .put("format", FORMAT)
        .put("exportedAt", exportedAt)
        .put("journal", JSONArray(c.journal.map { e ->
            JSONObject()
                .put("uuid", e.uuid)
                .put("day", e.day)
                .put("herbId", e.herbId ?: JSONObject.NULL)
                .put("name", e.name)
                .put("steepSeconds", e.steepSeconds ?: JSONObject.NULL)
                .put("note", e.note)
                .put("rating", e.rating ?: JSONObject.NULL)
                .put("updatedAt", e.updatedAt)
        }))
        .put("cupboard", JSONArray(c.cupboard.map { i ->
            JSONObject()
                .put("uuid", i.uuid)
                .put("herbId", i.herbId ?: JSONObject.NULL)
                .put("name", i.name)
                .put("boughtDay", i.boughtDay ?: JSONObject.NULL)
                .put("runningLow", i.runningLow)
                .put("updatedAt", i.updatedAt)
        }))
        .put("presets", JSONArray(c.presets.map { p ->
            JSONObject()
                .put("uuid", p.uuid)
                .put("name", p.name)
                .put("seconds", p.seconds)
                .put("tempF", p.tempF ?: JSONObject.NULL)
                .put("tempC", p.tempC ?: JSONObject.NULL)
                .put("updatedAt", p.updatedAt)
        }))
        .toString(2)

    /** Days from 1900 to 2200: anything outside is not a date this app wrote. */
    private val SENSIBLE_DAYS = LocalDate.of(1900, 1, 1).toEpochDay()..LocalDate.of(2200, 1, 1).toEpochDay()

    /** The longest steep the journal's own field can hold (999:59). */
    const val MAX_STEEP_SECONDS = 999 * 60 + 59

    /** The largest file read back in; a real one is a few kilobytes. */
    const val MAX_BYTES = 5L * 1024 * 1024

    private fun sensibleDay(day: Long) = day in SENSIBLE_DAYS

    /** Reads a whole file before anything is written, so a bad one changes nothing. */
    fun decode(text: String): Contents {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw NotABackup("not JSON")
        }
        if (root.optString("kind") != KIND) throw NotABackup("not a Herbal backup")
        if (root.optInt("format", 0) > FORMAT) throw NotABackup("made by a newer version")
        return try {
            Contents(
                journal = root.objects("journal").map { o ->
                    JournalEntry(
                        uuid = o.getString("uuid"),
                        day = o.getLong("day").also { if (!sensibleDay(it)) throw NotABackup("a date is out of range") },
                        herbId = o.text("herbId"),
                        name = o.getString("name"),
                        steepSeconds = o.int("steepSeconds")?.takeIf { it in 1..MAX_STEEP_SECONDS },
                        note = o.optString("note"),
                        rating = o.int("rating")?.takeIf { it in 1..5 },
                        updatedAt = o.optLong("updatedAt"),
                    )
                },
                cupboard = root.objects("cupboard").map { o ->
                    CupboardItem(
                        uuid = o.getString("uuid"),
                        herbId = o.text("herbId"),
                        name = o.getString("name"),
                        boughtDay = if (o.isNull("boughtDay")) null else o.getLong("boughtDay")
                            .also { if (!sensibleDay(it)) throw NotABackup("a date is out of range") },
                        runningLow = o.optBoolean("runningLow"),
                        updatedAt = o.optLong("updatedAt"),
                    )
                },
                presets = root.objects("presets").map { o ->
                    val f = o.int("tempF")?.takeIf { it in 32..212 }
                    val c = o.int("tempC")?.takeIf { it in 0..100 }
                    OwnPreset(
                        uuid = o.getString("uuid"),
                        name = o.getString("name"),
                        seconds = o.getInt("seconds").coerceIn(BrewMath.MIN_SECONDS, BrewMath.MAX_SECONDS),
                        tempF = if (f != null && c != null) f else null,
                        tempC = if (f != null && c != null) c else null,
                        updatedAt = o.optLong("updatedAt"),
                    )
                },
            )
        } catch (e: JSONException) {
            throw NotABackup("a row is missing something")
        }
    }

    suspend fun read(db: HerbalDatabase): Contents =
        Contents(db.journal().list(), db.cupboard().list(), db.presets().list())

    /** Adds every row not already here, in one transaction. Ids are the phone's own. */
    suspend fun restore(db: HerbalDatabase, c: Contents): Result = db.withTransaction {
        var added = 0
        var here = 0
        fun count(id: Long) = if (id == -1L) here++ else added++
        c.journal.forEach { count(db.journal().insert(it.copy(id = 0))) }
        c.cupboard.forEach { count(db.cupboard().insert(it.copy(id = 0))) }
        c.presets.forEach { count(db.presets().insert(it.copy(id = 0))) }
        Result(added, here)
    }

    private fun JSONObject.objects(name: String): List<JSONObject> {
        val a = optJSONArray(name) ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    private fun JSONObject.text(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private fun JSONObject.int(name: String): Int? =
        if (isNull(name) || !has(name)) null else getInt(name)
}
