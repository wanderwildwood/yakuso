package com.wanderwildwood.yakuso.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The copy of the journal, cupboard and presets, out and back in. Made-up rows only. */
@RunWith(RobolectricTestRunner::class)
class BackupTest {
    private lateinit var db: HerbalDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HerbalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun close() = db.close()

    private val contents = Backup.Contents(
        journal = listOf(
            JournalEntry(uuid = "j1", day = 20_412, herbId = "chamomile", name = "Chamomile", steepSeconds = 300, note = "before bed", rating = 4, updatedAt = 10),
            JournalEntry(uuid = "j2", day = 20_413, herbId = null, name = "Grandmother's blend", steepSeconds = null, note = "", rating = null, updatedAt = 11),
        ),
        cupboard = listOf(
            CupboardItem(uuid = "c1", herbId = "peppermint", name = "Peppermint", boughtDay = 20_380, runningLow = true, updatedAt = 12),
            CupboardItem(uuid = "c2", herbId = null, name = "Genmaicha", boughtDay = null, runningLow = false, updatedAt = 13),
        ),
        presets = listOf(
            OwnPreset(uuid = "p1", name = "Sencha", seconds = 90, tempF = 175, tempC = 80, updatedAt = 14),
            OwnPreset(uuid = "p2", name = "Long mint", seconds = 600, tempF = null, tempC = null, updatedAt = 15),
        ),
    )

    private fun Backup.Contents.withoutIds() = Backup.Contents(
        journal.map { it.copy(id = 0) }, cupboard.map { it.copy(id = 0) }, presets.map { it.copy(id = 0) },
    )

    @Test fun aCopyReadsBackAsItWasWritten() {
        val back = Backup.decode(Backup.encode(contents, 1_000))
        assertEquals(contents, back)
    }

    @Test fun aRestoreIntoAnEmptyAppBringsEverything() = runTest {
        val r = Backup.restore(db, Backup.decode(Backup.encode(contents, 1_000)))
        assertEquals(Backup.Result(added = 6, alreadyHere = 0), r)
        val now = Backup.read(db).withoutIds()
        assertEquals(contents.journal.sortedBy { it.uuid }, now.journal.sortedBy { it.uuid })
        assertEquals(contents.cupboard.sortedBy { it.uuid }, now.cupboard.sortedBy { it.uuid })
        assertEquals(contents.presets, now.presets)
    }

    @Test fun aRestoreOnlyAddsAndLeavesWhatIsHereAlone() = runTest {
        // Already here, and changed since the copy was made: the change stays.
        db.journal().insert(contents.journal[0].copy(note = "changed on the phone"))
        db.cupboard().insert(CupboardItem(uuid = "c9", herbId = null, name = "Hojicha", boughtDay = null, runningLow = false, updatedAt = 1))
        val r = Backup.restore(db, contents)
        assertEquals(Backup.Result(added = 5, alreadyHere = 1), r)
        assertEquals("changed on the phone", db.journal().list().single { it.uuid == "j1" }.note)
        assertEquals(3, db.cupboard().list().size)
        // The same file again changes nothing.
        assertEquals(Backup.Result(added = 0, alreadyHere = 6), Backup.restore(db, contents))
    }

    @Test fun whatIsNotACopyIsRefusedBeforeAnythingIsWritten() = runTest {
        assertThrows(Backup.NotABackup::class.java) { Backup.decode("not json at all") }
        assertThrows(Backup.NotABackup::class.java) { Backup.decode("""{"kind":"something-else"}""") }
        assertThrows(Backup.NotABackup::class.java) { Backup.decode("""{"kind":"herbal-backup","format":99}""") }
        assertThrows(Backup.NotABackup::class.java) {
            Backup.decode("""{"kind":"herbal-backup","format":1,"journal":[{"uuid":"x"}]}""")
        }
        assertEquals(0, db.journal().list().size)
    }

    @Test fun aRatingOutsideOneToFiveIsDropped() {
        val text = """{"kind":"herbal-backup","format":1,"journal":[{"uuid":"x","day":1,"name":"Linden","rating":9}]}"""
        assertEquals(null, Backup.decode(text).journal.single().rating)
    }
}
