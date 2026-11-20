package com.wanderwildwood.yakuso.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.and
import androidx.compose.ui.test.or
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.yakuso.herbs.HerbBook
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Finder's results as they are drawn, for the needs where a wrong herb would do harm.
 * Decided 2026-11-20: pregnancy is ginger alone; nursing is fennel, lemon balm and marshmallow
 * root; every result page says it is for everyday complaints, and these say to ask first.
 */
@RunWith(RobolectricTestRunner::class)
class FinderScreenTest {
    @get:Rule val compose = createComposeRule()

    private val herbs = HerbBook.parse(File("src/main/assets/herbs.json").readText())
    private val everyday = "For everyday complaints. See a doctor for anything serious or lasting."
    private val askFirst = "Ask your midwife or doctor first."

    private fun show(need: String) {
        compose.setContent {
            ThemeMMD(colorScheme = monochrome, typography = houseTypography) {
                FinderResults(need, herbs, cupboard = emptySet(), onOpen = {}, onBack = {})
            }
        }
    }

    /** The herb rows on the page: every press that names an herb from the book. */
    private fun assertRowsAre(names: List<String>) {
        names.forEach { compose.onNodeWithText(it).assertExists() }
        val others = herbs.map { it.name }.filterNot { it in names }
        others.forEach { compose.onAllNodesWithText(it).assertCountEquals(0) }
        compose.onAllNodes(hasClickAction() and herbs.map { hasText(it.name) }.reduce { a, b -> a or b })
            .assertCountEquals(names.size)
    }

    @Test fun pregnancyIsGingerAloneAndSaysToAskFirst() {
        show("pregnancy")
        assertRowsAre(listOf("Ginger"))
        compose.onNodeWithText(everyday).assertExists()
        compose.onNodeWithText(askFirst).assertExists()
    }

    @Test fun nursingIsFennelLemonBalmAndMarshmallowRootAndSaysToAskFirst() {
        show("nursing")
        assertRowsAre(listOf("Fennel", "Lemon balm", "Marshmallow root"))
        compose.onNodeWithText(everyday).assertExists()
        compose.onNodeWithText(askFirst).assertExists()
    }

    @Test fun anEverydayNeedSaysSoAndDoesNotAskFirst() {
        show("sleep")
        compose.onNodeWithText(everyday).assertExists()
        compose.onAllNodesWithText(askFirst).assertCountEquals(0)
        compose.onNodeWithText("Chamomile").assertExists()
    }
}
