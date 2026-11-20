package com.wanderwildwood.yakuso.data

import com.wanderwildwood.yakuso.timer.BrewMath
import com.wanderwildwood.yakuso.ui.readAtMost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A damaged or hand-made copy: numbers no screen of this app could have written. */
@RunWith(RobolectricTestRunner::class)
class BackupRangeTest {
    private fun file(journal: String = "[]", cupboard: String = "[]", presets: String = "[]") =
        """{"kind":"herbal-backup","format":1,"journal":$journal,"cupboard":$cupboard,"presets":$presets}"""

    @Test fun aJournalDayNoCalendarHoldsRefusesTheWholeFile() {
        assertThrows(Backup.NotABackup::class.java) {
            Backup.decode(file(journal = """[{"uuid":"j","day":1000000000000000,"name":"Mint"}]"""))
        }
    }

    @Test fun aBoughtDayFarOutRefusesTheWholeFile() {
        assertThrows(Backup.NotABackup::class.java) {
            Backup.decode(file(cupboard = """[{"uuid":"c","name":"Mint","boughtDay":-99999999}]"""))
        }
    }

    @Test fun aSensibleDayStillReads() {
        val c = Backup.decode(file(journal = """[{"uuid":"j","day":20412,"name":"Mint"}]"""))
        assertEquals(20_412L, c.journal.single().day)
    }

    @Test fun aSteepOutOfRangeIsDropped() {
        val c = Backup.decode(file(journal = """[{"uuid":"j","day":20412,"name":"Mint","steepSeconds":-30}]"""))
        assertNull(c.journal.single().steepSeconds)
    }

    @Test fun aPresetLengthIsKeptInsideTheTimersRange() {
        val c = Backup.decode(file(presets = """[{"uuid":"a","name":"Zero","seconds":0},{"uuid":"b","name":"Huge","seconds":99999999}]"""))
        assertEquals(listOf(BrewMath.MIN_SECONDS, BrewMath.MAX_SECONDS), c.presets.map { it.seconds })
    }

    @Test fun waterThatIsNotWaterIsDropped() {
        val c = Backup.decode(file(presets = """[{"uuid":"a","name":"Ice","seconds":60,"tempF":-40,"tempC":-40}]"""))
        assertNull(c.presets.single().tempF)
        assertNull(c.presets.single().tempC)
    }

    @Test fun aFileIsReadNoFurtherThanTheCap() {
        val big = ByteArray(10_000)
        assertEquals(4_097, readAtMost(big.inputStream(), 4_097).size)
        assertEquals(10_000, readAtMost(big.inputStream(), 1_000_000).size)
    }
}
