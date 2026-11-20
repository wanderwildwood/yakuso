package com.wanderwildwood.yakuso.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.yakuso.R
import com.wanderwildwood.yakuso.herbs.Herb
import com.wanderwildwood.yakuso.herbs.HerbBook

/**
 * The start screen: every herb, A to Z, with a search field over it. A row is the common name,
 * the Latin name under it, a check when it is in the cupboard, and the caution mark at the end
 * when the herb has a serious caution. Over the search field, one line of doors to the rest of
 * the app (the rows under Mushroom Journal's title, made one line so the list keeps the screen).
 */
@Composable
fun HerbsScreen(
    herbs: List<Herb>,
    cupboard: Set<String>,
    onOpen: (Herb) -> Unit,
    onDoor: (Door) -> Unit,
    onSettings: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(herbs, query) { HerbBook.matching(herbs, query) }
    val listState = rememberLazyListState()
    // A new search starts at the top of what it found.
    LaunchedEffect(query) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Bar(stringResource(R.string.herbs_title)) {
            BarButton(Icons.Settings, stringResource(R.string.cd_settings), onSettings)
        }
        Doors(onDoor)
        TextFieldMMD(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            placeholder = { TextMMD(stringResource(R.string.herbs_search)) },
            singleLine = true,
        )
        if (shown.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TextMMD(stringResource(R.string.herbs_no_match), style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Box(Modifier.weight(1f).padding(top = 8.dp)) {
                PagedColumn(state = listState, showCutRow = true) {
                    items(shown, key = { it.id }) { herb ->
                        HerbRow(herb, inCupboard = herb.id in cupboard, onOpen = { onOpen(herb) })
                        DottedRule()
                    }
                }
            }
        }
    }
}

/** The other screens, reached from the line of doors on the start screen. */
enum class Door { FINDER, TIMER, JOURNAL, CUPBOARD }

/** Four words side by side, each a 48 dp press, and the dotted rule under them. */
@Composable
fun Doors(onDoor: (Door) -> Unit) {
    val names = mapOf(
        Door.FINDER to stringResource(R.string.door_finder),
        Door.TIMER to stringResource(R.string.door_timer),
        Door.JOURNAL to stringResource(R.string.door_journal),
        Door.CUPBOARD to stringResource(R.string.door_cupboard),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Door.entries.forEach { d ->
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clickable { onDoor(d) },
                contentAlignment = Alignment.Center,
            ) {
                TextMMD(names.getValue(d), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
    DottedRule()
}

/** One herb in a list: name, Latin name, the cupboard's check and the caution mark. */
@Composable
fun HerbRow(herb: Herb, inCupboard: Boolean, onOpen: () -> Unit) {
    val serious = stringResource(R.string.herbs_cd_serious)
    val have = stringResource(R.string.herbs_cd_in_cupboard)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            TextMMD(herb.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (herb.latin.isNotBlank()) TextMMD(herb.latin, style = MaterialTheme.typography.bodySmall)
        }
        if (inCupboard) {
            TextMMD(
                stringResource(R.string.cupboard_mark),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp).semantics { contentDescription = have },
            )
        }
        if (herb.serious) {
            TextMMD(
                stringResource(R.string.caution_mark),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp).semantics { contentDescription = serious },
            )
        }
    }
}
