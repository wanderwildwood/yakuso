package com.wanderwildwood.yakuso.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.yakuso.R
import com.wanderwildwood.yakuso.data.HerbalDatabase
import com.wanderwildwood.yakuso.data.JournalEntry
import com.wanderwildwood.yakuso.herbs.Herb
import com.wanderwildwood.yakuso.timer.BrewMath
import com.wanderwildwood.yakuso.timer.BrewTimer
import java.time.LocalDate
import java.util.UUID

/** What I brewed: newest first. A row opens the entry. */
@Composable
fun JournalScreen(onOpen: (Long) -> Unit, onNew: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { HerbalDatabase.get(context) }
    val entries by remember(db) { db.journal().all() }.collectAsState(initial = null)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Bar(stringResource(R.string.journal_title), onBack = onBack) {
            BarButton(Icons.Add, stringResource(R.string.journal_cd_add), onNew)
        }
        val list = entries ?: return@Column
        if (list.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TextMMD(stringResource(R.string.journal_empty), style = MaterialTheme.typography.bodySmall)
            }
            return@Column
        }
        Box(Modifier.weight(1f)) {
            PagedColumn(showCutRow = true) {
                items(list, key = { it.id }) { e ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onOpen(e.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        TextMMD(e.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        TextMMD(journalLine(e), style = MaterialTheme.typography.bodySmall)
                        if (e.note.isNotBlank()) {
                            TextMMD(e.note, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    DottedRule()
                }
            }
        }
    }
}

/** "Fri, Nov 20 · steeped 5 min · 4 of 5": only what was said. */
@Composable
private fun journalLine(e: JournalEntry): String = listOfNotNull(
    dayText(e.day),
    e.steepSeconds?.let { stringResource(R.string.journal_steeped, BrewMath.minutes(it)) },
    e.rating?.let { stringResource(R.string.journal_rating, it) },
).joinToString(stringResource(R.string.list_separator))

/**
 * One entry, new or kept. [id] null is a new one; [fromBrew] fills it from the timer that just
 * finished. Back keeps what was written; an entry with no name is not kept. Delete asks on its
 * own row, last.
 */
@Composable
fun JournalEntryScreen(id: Long?, fromBrew: Boolean, herbs: List<Herb>, onDone: () -> Unit) {
    val context = LocalContext.current
    val db = remember { HerbalDatabase.get(context) }

    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var uuid by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var day by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var name by rememberSaveable { mutableStateOf("") }
    var herbId by rememberSaveable { mutableStateOf<String?>(null) }
    var steep by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var filled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (filled) return@LaunchedEffect
        if (id != null) {
            db.journal().find(id)?.let { e ->
                uuid = e.uuid; day = e.day; name = e.name; herbId = e.herbId
                steep = e.steepSeconds?.let { BrewMath.minutes(it) }.orEmpty()
                note = e.note; rating = e.rating ?: 0
            }
            loaded = true
        } else if (fromBrew) {
            val b = BrewTimer.current(context)
            name = b.label; herbId = b.herbId; steep = BrewMath.minutes(b.seconds)
        }
        filled = true
    }

    fun keep() {
        val n = name.trim()
        if (n.isEmpty() || !loaded) return
        val entry = JournalEntry(
            id = id ?: 0,
            uuid = uuid,
            day = day,
            herbId = herbId ?: herbNamed(herbs, n)?.id,
            name = n,
            steepSeconds = parseSteep(steep),
            note = note.trim(),
            rating = rating.takeIf { it in 1..5 },
            updatedAt = System.currentTimeMillis(),
        )
        HerbalDatabase.write(context) { if (id == null) journal().insert(entry) else journal().update(entry) }
    }

    val leave = { keep(); onDone() }
    androidx.activity.compose.BackHandler(onBack = leave)
    val delete = rememberArmedFlag()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Bar(stringResource(if (id == null) R.string.entry_new_title else R.string.entry_title), onBack = leave)
        Box(Modifier.weight(1f)) {
            PagedColumn(contentPadding = PaddingValues(start = 20.dp, end = 12.dp)) {
                item(key = "name") {
                    FieldLabel(stringResource(R.string.entry_what))
                    NameField(
                        value = name,
                        herbs = herbs,
                        hint = stringResource(R.string.entry_what_hint),
                        onChange = { name = it; if (herbId != null && herbNamed(herbs, it)?.id != herbId) herbId = null },
                        onPick = { h -> name = h.name; herbId = h.id },
                    )
                }
                item(key = "day") {
                    FieldLabel(stringResource(R.string.entry_day))
                    TextMMD(dayText(day), style = MaterialTheme.typography.bodyMedium)
                    ButtonRow(
                        stringResource(R.string.date_day_earlier) to { day -= 1 },
                        stringResource(R.string.date_today) to { day = LocalDate.now().toEpochDay() },
                        stringResource(R.string.date_day_later) to { day += 1 },
                    )
                }
                item(key = "steep") {
                    FieldLabel(stringResource(R.string.entry_steep))
                    TextFieldMMD(
                        value = steep,
                        onValueChange = { steep = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        placeholder = { TextMMD(stringResource(R.string.entry_steep_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                item(key = "rating") {
                    FieldLabel(stringResource(R.string.entry_rating))
                    // Pressing the chosen one again takes the rating away: it is optional.
                    HouseChoiceRow(
                        options = (1..5).map { it.toString() },
                        selected = rating - 1,
                        onSelect = { i -> rating = if (rating == i + 1) 0 else i + 1 },
                        compact = true,
                    )
                }
                item(key = "note") {
                    FieldLabel(stringResource(R.string.entry_note))
                    TextFieldMMD(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        placeholder = { TextMMD(stringResource(R.string.entry_note_hint)) },
                    )
                }
                item(key = "done") {
                    HouseButton(stringResource(R.string.entry_done), leave, Modifier.fillMaxWidth().padding(top = 16.dp), on = true)
                }
                if (id != null) {
                    item(key = "delete") {
                        DeleteRow(
                            label = stringResource(R.string.entry_delete),
                            armedLabel = stringResource(R.string.entry_delete_armed),
                            armed = delete.on,
                        ) {
                            delete.press {
                                HerbalDatabase.write(context) { journal().delete(id) }
                                onDone()
                            }
                        }
                    }
                }
            }
        }
    }
}
