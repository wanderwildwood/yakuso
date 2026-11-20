package com.wanderwildwood.yakuso.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.yakuso.R
import com.wanderwildwood.yakuso.data.CupboardItem
import com.wanderwildwood.yakuso.data.HerbalDatabase
import com.wanderwildwood.yakuso.herbs.Herb
import java.time.LocalDate
import java.util.UUID

/** What is on hand, A to Z. A row opens it. */
@Composable
fun CupboardScreen(onOpen: (Long) -> Unit, onNew: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { HerbalDatabase.get(context) }
    val items by remember(db) { db.cupboard().all() }.collectAsState(initial = null)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Bar(stringResource(R.string.cupboard_title), onBack = onBack)
        val list = items ?: return@Column
        Box(Modifier.weight(1f)) {
            PagedColumn(contentPadding = PaddingValues(start = 20.dp, end = 12.dp), showCutRow = true) {
                item(key = "add") {
                    OutlinedButtonMMD(onClick = onNew, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).height(48.dp)) {
                        TextMMD(text = stringResource(R.string.cupboard_add), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (list.isEmpty()) {
                    item(key = "empty") {
                        TextMMD(
                            stringResource(R.string.cupboard_empty),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        )
                    }
                }
                items(list, key = { it.id }) { i ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onOpen(i.id) }.padding(vertical = 10.dp),
                    ) {
                        TextMMD(i.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        cupboardRowLine(i)?.let { TextMMD(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    DottedRule(start = 0.dp, end = 0.dp)
                }
            }
        }
    }
}

/** "Bought Nov 2 · About to run out": only what was said; nothing when nothing was. */
@Composable
private fun cupboardRowLine(i: CupboardItem): String? = listOfNotNull(
    i.boughtDay?.let { stringResource(R.string.cupboard_bought, dayText(it)) },
    if (i.runningLow) stringResource(R.string.cupboard_running_low) else null,
).takeIf { it.isNotEmpty() }?.joinToString(stringResource(R.string.list_separator))

/** One thing in the cupboard, new ([id] null) or kept. Back keeps it; no name, nothing kept. */
@Composable
fun CupboardItemScreen(id: Long?, herbs: List<Herb>, onDone: () -> Unit) {
    val context = LocalContext.current
    val db = remember { HerbalDatabase.get(context) }

    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var uuid by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var name by rememberSaveable { mutableStateOf("") }
    var herbId by rememberSaveable { mutableStateOf<String?>(null) }
    var bought by rememberSaveable { mutableStateOf<Long?>(null) }
    var low by rememberSaveable { mutableStateOf(false) }
    var filled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (filled || id == null) return@LaunchedEffect
        db.cupboard().find(id)?.let { i ->
            uuid = i.uuid; name = i.name; herbId = i.herbId; bought = i.boughtDay; low = i.runningLow
        }
        loaded = true
        filled = true
    }

    fun keep() {
        val n = name.trim()
        if (n.isEmpty() || !loaded) return
        val item = CupboardItem(
            id = id ?: 0,
            uuid = uuid,
            herbId = herbId ?: herbNamed(herbs, n)?.id,
            name = n,
            boughtDay = bought,
            runningLow = low,
            updatedAt = System.currentTimeMillis(),
        )
        HerbalDatabase.write(context) { if (id == null) cupboard().insert(item) else cupboard().update(item) }
    }

    val leave = { keep(); onDone() }
    BackHandler(onBack = leave)
    val delete = rememberArmedFlag()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Bar(stringResource(if (id == null) R.string.cupboard_new_title else R.string.cupboard_item_title), onBack = leave)
        Box(Modifier.weight(1f)) {
            PagedColumn(contentPadding = PaddingValues(start = 20.dp, end = 12.dp)) {
                item(key = "name") {
                    FieldLabel(stringResource(R.string.cupboard_what))
                    NameField(
                        value = name,
                        herbs = herbs,
                        hint = stringResource(R.string.entry_what_hint),
                        onChange = { name = it; if (herbId != null && herbNamed(herbs, it)?.id != herbId) herbId = null },
                        onPick = { h -> name = h.name; herbId = h.id },
                    )
                }
                item(key = "bought") {
                    FieldLabel(stringResource(R.string.cupboard_bought_label))
                    TextMMD(
                        bought?.let { dayText(it) } ?: stringResource(R.string.cupboard_bought_unsaid),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ButtonRow(
                        stringResource(R.string.date_today) to { bought = LocalDate.now().toEpochDay() },
                        stringResource(R.string.date_clear) to { bought = null },
                    )
                    ButtonRow(
                        stringResource(R.string.date_day_earlier) to { bought = (bought ?: LocalDate.now().toEpochDay()) - 1 },
                        stringResource(R.string.date_month_earlier) to {
                            bought = LocalDate.ofEpochDay(bought ?: LocalDate.now().toEpochDay()).minusMonths(1).toEpochDay()
                        },
                    )
                }
                item(key = "low") {
                    SwitchRow(stringResource(R.string.cupboard_running_low), low) { low = it }
                }
                item(key = "done") {
                    HouseButton(stringResource(R.string.entry_done), leave, Modifier.fillMaxWidth().padding(top = 16.dp), on = true)
                }
                if (id != null) {
                    item(key = "delete") {
                        DeleteRow(
                            label = stringResource(R.string.cupboard_delete),
                            armedLabel = stringResource(R.string.cupboard_delete_armed),
                            armed = delete.on,
                        ) {
                            delete.press {
                                HerbalDatabase.write(context) { cupboard().delete(id) }
                                onDone()
                            }
                        }
                    }
                }
            }
        }
    }
}
