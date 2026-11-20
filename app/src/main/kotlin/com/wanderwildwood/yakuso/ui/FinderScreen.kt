package com.wanderwildwood.yakuso.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.yakuso.R
import com.wanderwildwood.yakuso.herbs.Herb
import com.wanderwildwood.yakuso.herbs.HerbBook
import com.wanderwildwood.yakuso.herbs.NEEDS

/** The needs whose results also say to ask a midwife or doctor first (decided 2026-11-20). */
val ASK_FIRST: Set<String> = setOf("period-cramps", "pregnancy", "nursing")

/** The Finder's first page: the everyday needs, in the book's order. */
@Composable
fun FinderScreen(onNeed: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Bar(stringResource(R.string.finder_title), onBack = onBack)
        Box(Modifier.weight(1f)) {
            PagedColumn {
                items(NEEDS, key = { it }) { need ->
                    TextMMD(
                        needName(need),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onNeed(need) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    DottedRule()
                }
            }
        }
    }
}

/**
 * The herbs for one need, A to Z, each with its marks. The page opens with the same line every
 * time ("for everyday complaints"), and period cramps, pregnancy and nursing add "ask your midwife
 * or doctor first". Which herbs come here is the book's: pregnancy is ginger alone, nursing
 * fennel, lemon balm and marshmallow root, and tools/check-cautions.py keeps it so.
 */
@Composable
fun FinderResults(
    need: String,
    herbs: List<Herb>,
    cupboard: Set<String>,
    onOpen: (Herb) -> Unit,
    onBack: () -> Unit,
) {
    val found = HerbBook.forNeed(herbs, need)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Bar(needName(need), onBack = onBack)
        Box(Modifier.weight(1f)) {
            PagedColumn(showCutRow = true) {
                item(key = "everyday") {
                    Note(stringResource(R.string.finder_everyday))
                }
                if (need in ASK_FIRST) {
                    item(key = "ask") { Note(stringResource(R.string.finder_ask_first), bold = true) }
                }
                item(key = "rule") { DottedRule() }
                items(found, key = { it.id }) { herb ->
                    HerbRow(herb, inCupboard = herb.id in cupboard, onOpen = { onOpen(herb) })
                    DottedRule()
                }
            }
        }
    }
}

@Composable
private fun Note(text: String, bold: Boolean = false) {
    TextMMD(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun needName(need: String): String = stringResource(
    when (need) {
        "sleep" -> R.string.need_sleep
        "calm" -> R.string.need_calm
        "digestion" -> R.string.need_digestion
        "colds" -> R.string.need_colds
        "sore-throat" -> R.string.need_sore_throat
        "nausea" -> R.string.need_nausea
        "headache" -> R.string.need_headache
        "period-cramps" -> R.string.need_period_cramps
        "pregnancy" -> R.string.need_pregnancy
        else -> R.string.need_nursing
    },
)
