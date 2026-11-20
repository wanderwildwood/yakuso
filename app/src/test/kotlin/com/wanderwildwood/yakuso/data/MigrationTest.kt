package com.wanderwildwood.yakuso.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The baseline every later migration is tested from: a database file made from the exported
 * schema of a version (app/schemas, committed), with a row in each table, opened by the app's own
 * builder and migration list. Room checks the opened file against the current entities and
 * throws if a migration and an entity have drifted apart. Today there is one version, so this
 * proves the exported 1.json is the schema the app builds, and that it opens with its rows; when
 * version 2 comes, the same test starts from 1.json and must reach 2 with nothing lost.
 * (Mushroom Journal's MigrationTest, reading the schema file rather than copying it.)
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"
    private val schemas = File("schemas/${HerbalDatabase::class.java.name}")
    private var db: HerbalDatabase? = null

    @After fun close() {
        db?.close()
        context.deleteDatabase(name)
    }

    @Test fun everyVersionHasItsSchemaCommittedAndAMigrationOnward() {
        (1..HerbalDatabase.VERSION).forEach { v ->
            assertTrue("no exported schema for version $v in $schemas", File(schemas, "$v.json").isFile)
        }
        val steps = HerbalDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        (1 until HerbalDatabase.VERSION).forEach { v ->
            assertTrue("no migration from $v to ${v + 1}", (v to v + 1) in steps)
        }
    }

    @Test fun everyOldVersionOpensAtTheCurrentOneWithItsRows() = runTest {
        (1..HerbalDatabase.VERSION).forEach { v ->
            context.deleteDatabase(name)
            write(v)
            db = Room.databaseBuilder(context, HerbalDatabase::class.java, name)
                .addMigrations(*HerbalDatabase.MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            val d = db!!
            assertEquals("journal from version $v", "kept through the upgrade", d.journal().list().single().note)
            assertEquals("cupboard from version $v", "Rooibos", d.cupboard().list().single().name)
            assertEquals("presets from version $v", 80, d.presets().list().single().tempC)
            d.close()
            db = null
        }
    }

    /** A file at version [v], made from its exported schema, with one made-up row per table. */
    private fun write(v: Int) {
        val schema = JSONObject(File(schemas, "$v.json").readText()).getJSONObject("database")
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sql ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) {
                    sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) sql.execSQL(setup.getString(i))
            // Version 1's rows; a later version's columns would be added here as they come.
            sql.execSQL(
                "INSERT INTO journal (uuid, day, herbId, name, steepSeconds, note, rating, updatedAt) " +
                    "VALUES ('j', 20412, 'chamomile', 'Chamomile', 300, 'kept through the upgrade', 4, 1)",
            )
            sql.execSQL(
                "INSERT INTO cupboard (uuid, herbId, name, boughtDay, runningLow, updatedAt) " +
                    "VALUES ('c', 'rooibos', 'Rooibos', NULL, 1, 1)",
            )
            sql.execSQL(
                "INSERT INTO presets (uuid, name, seconds, tempF, tempC, updatedAt) VALUES ('p', 'Sencha', 90, 175, 80, 1)",
            )
            sql.version = v
        }
    }
}
