package com.wanderwildwood.yakuso.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * the Latin name under it, and the mark at the end when the herb has a serious caution.
 */
@Composable
fun HerbsScreen(
    herbs: List<Herb>,
    onOpen: (Herb) -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(herbs, query) { HerbBook.matching(herbs, query) }
    val listState = rememberLazyListState()
    // A new search starts at the top of what it found.
    LaunchedEffect(query) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Bar(stringResource(R.string.herbs_title)) {
            BarButton(Icons.Settings, stringResource(R.string.cd_settings), onSettings)
            BarButton(Icons.Info, stringResource(R.string.cd_about), onAbout)
        }
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
                        HerbRow(herb, onOpen = { onOpen(herb) })
                        DottedRule()
                    }
                }
            }
        }
    }
}

@Composable
private fun HerbRow(herb: Herb, onOpen: () -> Unit) {
    val serious = stringResource(R.string.herbs_cd_serious)
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
        if (herb.serious) {
            TextMMD(
                stringResource(R.string.caution_mark),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp).semantics { contentDescription = serious },
            )
        }
    }
}
