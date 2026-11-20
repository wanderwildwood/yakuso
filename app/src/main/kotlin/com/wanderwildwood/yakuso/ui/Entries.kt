package com.wanderwildwood.yakuso.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.yakuso.R
import com.wanderwildwood.yakuso.herbs.Herb
import com.wanderwildwood.yakuso.herbs.HerbBook
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * What the journal and the cupboard share: a day as words, how long a brew steeped, and a name
 * field that offers the book's herbs as it is typed.
 */

/** "Fri, Nov 20" this year, "Nov 20, 2025" before it. */
internal fun dayText(epochDay: Long, today: LocalDate = LocalDate.now()): String {
    val d = LocalDate.ofEpochDay(epochDay)
    val pattern = if (d.year == today.year) "EEE, MMM d" else "MMM d, yyyy"
    return d.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
}

/**
 * How long a brew steeped, from what was typed: "4" is four minutes, "2:30" two and a half,
 * "4.5" four and a half. Null for nothing, or for anything else.
 */
internal fun parseSteep(text: String): Int? {
    val t = text.trim()
    if (t.isEmpty()) return null
    Regex("""(\d{1,3}):([0-5]\d)""").matchEntire(t)?.let { m ->
        return (m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()).takeIf { it > 0 }
    }
    return t.toDoubleOrNull()?.takeIf { it > 0 && it <= 600 }?.let { (it * 60).toInt() }
}

/** The book's herb for [name], when the words are exactly its name (case aside). */
internal fun herbNamed(herbs: List<Herb>, name: String): Herb? =
    herbs.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

/** Up to [max] herbs from the book matching what is typed; none until something is, or once it is one. */
internal fun suggestions(herbs: List<Herb>, typed: String, max: Int = 3): List<Herb> {
    if (typed.isBlank() || herbNamed(herbs, typed) != null) return emptyList()
    return HerbBook.matching(herbs, typed).filterNot { it.dontUse }.take(max)
}

/** A name field, with the book's matching herbs under it to pick from. */
@Composable
internal fun NameField(
    value: String,
    herbs: List<Herb>,
    hint: String,
    onChange: (String) -> Unit,
    onPick: (Herb) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        TextFieldMMD(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            placeholder = { TextMMD(hint) },
            singleLine = true,
        )
        suggestions(herbs, value).forEach { h ->
            TextMMD(
                stringResource(R.string.entry_from_book, h.name),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPick(h) }.padding(vertical = 12.dp),
            )
        }
    }
}

/** A field's name over it, in bold. */
@Composable
internal fun FieldLabel(text: String) {
    TextMMD(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
    )
}

/** Buttons side by side, equal widths. */
@Composable
internal fun ButtonRow(vararg buttons: Pair<String, () -> Unit>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(HOUSE_GAP)) {
        buttons.forEach { (label, action) -> HouseButton(label, action, Modifier.weight(1f), compact = true) }
    }
}

/** A row that asks before it deletes: "Delete this entry — tap again". */
@Composable
internal fun DeleteRow(label: String, armedLabel: String, armed: Boolean, onPress: () -> Unit) {
    TextMMD(
        if (armed) armedLabel else label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (armed) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onPress).padding(vertical = 14.dp),
    )
}
