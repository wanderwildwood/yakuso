package com.wanderwildwood.yakuso.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The journal, cupboard and preset tables as the screens use them. Made-up rows only. */
@RunWith(RobolectricTestRunner::class)
class HerbalDatabaseTest {
    private lateinit var db: HerbalDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HerbalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun close() = db.close()

    private fun entry(uuid: String, day: Long, name: String = "Chamomile", at: Long = 1L) =
        JournalEntry(uuid = uuid, day = day, herbId = "chamomile", name = name, steepSeconds = 300, note = "", rating = null, updatedAt = at)

    @Test fun theJournalIsNewestDayFirst() = runTest {
        db.journal().insert(entry("a", day = 20_000))
        db.journal().insert(entry("b", day = 20_002))
        db.journal().insert(entry("c", day = 20_001))
        db.journal().insert(entry("d", day = 20_002, at = 5))
        assertEquals(listOf("d", "b", "c", "a"), db.journal().all().first().map { it.uuid })
    }

    @Test fun anEntryCanBeChangedAndDeleted() = runTest {
        val id = db.journal().insert(entry("a", day = 20_000))
        db.journal().update(db.journal().find(id)!!.copy(note = "a little bitter", rating = 4))
        assertEquals("a little bitter", db.journal().find(id)!!.note)
        assertEquals(4, db.journal().find(id)!!.rating)
        db.journal().delete(id)
        assertNull(db.journal().find(id))
    }

    @Test fun theSameUuidTwiceIsOneRow() = runTest {
        assertTrue(db.journal().insert(entry("same", 1)) > 0)
        assertEquals(-1L, db.journal().insert(entry("same", 2, name = "Peppermint")))
        assertEquals("Chamomile", db.journal().list().single().name)
    }

    @Test fun theCupboardIsAtoZIgnoringCase() = runTest {
        listOf("rooibos", "Chamomile", "black tea").forEachIndexed { i, n ->
            db.cupboard().insert(CupboardItem(uuid = "u$i", herbId = null, name = n, boughtDay = null, runningLow = false, updatedAt = 1))
        }
        assertEquals(listOf("black tea", "Chamomile", "rooibos"), db.cupboard().all().first().map { it.name })
    }

    @Test fun aCupboardItemKeepsItsDateAndItsRunningLow() = runTest {
        val id = db.cupboard().insert(CupboardItem(uuid = "u", herbId = "ginger", name = "Ginger", boughtDay = 20_400, runningLow = false, updatedAt = 1))
        db.cupboard().update(db.cupboard().find(id)!!.copy(runningLow = true, boughtDay = null))
        val got = db.cupboard().find(id)!!
        assertTrue(got.runningLow)
        assertNull(got.boughtDay)
        db.cupboard().delete(id)
        assertTrue(db.cupboard().list().isEmpty())
    }

    @Test fun ownPresetsKeepTheirOrderAndTheirPairs() = runTest {
        db.presets().insert(OwnPreset(uuid = "p1", name = "Sencha", seconds = 90, tempF = 175, tempC = 80, updatedAt = 1))
        db.presets().insert(OwnPreset(uuid = "p2", name = "Mint", seconds = 420, tempF = null, tempC = null, updatedAt = 2))
        val all = db.presets().all().first()
        assertEquals(listOf("Sencha", "Mint"), all.map { it.name })
        assertEquals(80, all.first().tempC)
        db.presets().delete(all.first().id)
        assertEquals(listOf("Mint"), db.presets().list().map { it.name })
    }
}
