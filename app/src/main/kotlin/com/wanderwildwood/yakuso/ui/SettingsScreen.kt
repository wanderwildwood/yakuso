package com.wanderwildwood.yakuso.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.yakuso.FilesCreateDocument
import com.wanderwildwood.yakuso.FilesOpenDocument
import com.wanderwildwood.yakuso.R
import com.wanderwildwood.yakuso.data.Backup
import com.wanderwildwood.yakuso.data.HerbalDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Dark mode, then the copy of the journal, the cupboard and your own timer settings. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onAbout: () -> Unit) {
    val context = LocalContext.current
    val db = remember { HerbalDatabase.get(context) }
    val scope = rememberCoroutineScope()

    val save = rememberLauncherForActivityResult(FilesCreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { FootNotice.say(context, export(context, db, uri)) }
    }
    val open = rememberLauncherForActivityResult(FilesOpenDocument()) { uri ->
        if (uri != null) scope.launch { FootNotice.say(context, import(context, db, uri)) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // About sits here, top right, as in the house's other apps; the home bar keeps only the cog.
        Bar(stringResource(R.string.settings_title), onBack = onBack) {
            BarButton(Icons.Info, stringResource(R.string.cd_about), onAbout)
        }
        Box(Modifier.weight(1f)) {
            PagedColumn(contentPadding = PaddingValues(start = 20.dp, end = 12.dp)) {
                item(key = "dark") {
                    SwitchRow(
                        title = stringResource(R.string.settings_dark),
                        checked = DarkMode.on,
                        onChange = { DarkMode.set(context, it) },
                    )
                }
                item(key = "export") {
                    SettingsRow(stringResource(R.string.settings_export), stringResource(R.string.settings_export_note)) {
                        save.launch("herbal-${LocalDate.now()}.json")
                    }
                }
                item(key = "import") {
                    SettingsRow(stringResource(R.string.settings_import), stringResource(R.string.settings_import_note)) {
                        open.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }
                }
            }
        }
    }
}

/** A row that does something: its name, and a note only where the name cannot carry the fact. */
@Composable
private fun SettingsRow(title: String, note: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 12.dp)) {
        TextMMD(title, style = MaterialTheme.typography.bodyMedium)
        note?.let { TextMMD(it, style = MaterialTheme.typography.labelSmall) }
    }
}

private suspend fun export(context: Context, db: HerbalDatabase, uri: Uri): String = try {
    val text = Backup.encode(Backup.read(db), System.currentTimeMillis())
    withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
    }
    context.getString(R.string.export_saved)
} catch (e: Exception) {
    context.getString(R.string.export_failed)
}

private suspend fun import(context: Context, db: HerbalDatabase, uri: Uri): String {
    val text = try {
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                // Read at most one byte past the cap: a bigger file is not a copy from this app,
                // and reading all of it could run the phone out of memory.
                // (InputStream.readNBytes is API 33; the Kompakt is 31.)
                val bytes = readAtMost(input, Backup.MAX_BYTES + 1)
                if (bytes.size > Backup.MAX_BYTES) null else bytes.toString(Charsets.UTF_8)
            }
        }
    } catch (e: Exception) {
        return context.getString(R.string.import_could_not_open)
    } ?: return context.getString(R.string.import_too_big)
    val contents = try {
        Backup.decode(text)
    } catch (e: Backup.NotABackup) {
        return context.getString(R.string.import_not_a_backup)
    }
    val r = try {
        Backup.restore(db, contents)
    } catch (e: Exception) {
        // One transaction: when it fails, nothing from the file was kept.
        return context.getString(R.string.import_failed)
    }
    return when {
        r.added == 0 && r.alreadyHere == 0 -> context.getString(R.string.import_nothing)
        r.added == 0 -> context.getString(R.string.import_all_here)
        else -> context.resources.getQuantityString(R.plurals.import_added, r.added, r.added) +
            (if (r.alreadyHere > 0) context.getString(R.string.import_already_here, r.alreadyHere) else "")
    }
}

/** Up to [limit] bytes of [input], fewer when it ends first. */
internal fun readAtMost(input: java.io.InputStream, limit: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0L
    while (total < limit) {
        val n = input.read(buf, 0, minOf(buf.size.toLong(), limit - total).toInt())
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}
