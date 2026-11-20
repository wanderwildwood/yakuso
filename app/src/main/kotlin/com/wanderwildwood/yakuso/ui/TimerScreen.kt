package com.wanderwildwood.yakuso.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.yakuso.R
import com.wanderwildwood.yakuso.data.HerbalDatabase
import com.wanderwildwood.yakuso.data.OwnPreset
import com.wanderwildwood.yakuso.timer.Brew
import com.wanderwildwood.yakuso.timer.BrewMath
import com.wanderwildwood.yakuso.timer.BrewTimer
import com.wanderwildwood.yakuso.timer.Preset
import com.wanderwildwood.yakuso.timer.RingService
import com.wanderwildwood.yakuso.timer.Ringing
import com.wanderwildwood.yakuso.timer.Temp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The brew timer: what is brewing, the time left in large figures, and the presets under it.
 * The figures move every ten seconds and every second only in the last ten (BrewMath), since
 * each move is a redraw of the panel. A finished brew offers to go in the journal.
 */
@Composable
fun TimerScreen(onAddToJournal: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val brew by BrewTimer.brew(context).collectAsState()
    val b = brew ?: BrewTimer.current(context)
    val ringing by RingService.ringing.collectAsState()
    val db = remember { HerbalDatabase.get(context) }
    val own by remember(db) { db.presets().all() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var naming by rememberSaveable { mutableStateOf(false) }

    // The clock: redrawn only when what it shows changes.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(b.running, b.endAt) {
        while (b.running) {
            now = System.currentTimeMillis()
            val left = b.endAt - now
            if (left <= 0) break
            delay(BrewMath.nextRedrawMs(left))
        }
        now = System.currentTimeMillis()
    }
    val shown = if (b.finished) 0L else BrewMath.shownSeconds(b.remainingMs(now))

    // What would stop it ringing on time, read each time the screen is drawn afresh.
    var problems by remember { mutableStateOf(timerProblems(context)) }
    LaunchedEffect(Unit) { problems = timerProblems(context) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Bar(stringResource(R.string.timer_title), onBack = onBack)
        problems.forEach { p ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextMMD(stringResource(p.text), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                HouseButton(stringResource(p.button), {
                    runCatching { context.startActivity(p.open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, Modifier.padding(start = 8.dp), compact = true)
            }
        }

        // What is brewing.
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextMMD(
                b.label.ifBlank { stringResource(R.string.timer_title) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            b.temp?.let { t ->
                TextMMD(tempText(t), style = MaterialTheme.typography.bodyMedium)
            }
            val cd = stringResource(R.string.timer_cd_left, BrewMath.clock(shown))
            TextMMD(
                BrewMath.clock(shown),
                // The figure the timer shows is an instrument's reading, not type (STYLE.md).
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 88.sp, lineHeight = 96.sp),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp).semantics { contentDescription = cd },
            )
            if (b.finished) {
                TextMMD(stringResource(R.string.timer_ready), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        // What can be done now.
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                b.finished -> {
                    if (ringing) {
                        HouseButton(stringResource(R.string.ring_stop), { Ringing.stop(context) }, Modifier.fillMaxWidth(), on = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HouseButton(stringResource(R.string.timer_add_to_journal), {
                            Ringing.stop(context)
                            BrewTimer.done(context)
                            onAddToJournal()
                        }, Modifier.weight(1f), on = !ringing)
                        HouseButton(stringResource(R.string.timer_done), { BrewTimer.done(context) }, Modifier.weight(1f))
                    }
                }
                b.running -> HouseButton(stringResource(R.string.timer_stop), { BrewTimer.cancel(context) }, Modifier.fillMaxWidth())
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HouseButton(stringResource(R.string.timer_less), {
                        BrewTimer.set(context, b.copy(seconds = BrewMath.adjust(b.seconds, -BrewMath.STEP_SECONDS)))
                    }, Modifier.weight(1f))
                    HouseButton(stringResource(R.string.timer_start), {
                        BrewTimer.start(context)
                        problems = timerProblems(context)
                    }, Modifier.weight(1.4f), on = true)
                    HouseButton(stringResource(R.string.timer_more), {
                        BrewTimer.set(context, b.copy(seconds = BrewMath.adjust(b.seconds, BrewMath.STEP_SECONDS)))
                    }, Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        DottedRule()

        // The presets, only while nothing runs: a press sets the timer, and Start starts it.
        Box(Modifier.weight(1f)) {
            if (!b.running && !b.finished) {
                val presets = BrewMath.BUILT_IN + own.map { it.asPreset() }
                var armedId by rememberArmedKey<Long>()
                PagedColumn(showCutRow = true) {
                    items(presets, key = { it.key }) { p ->
                        val name = Ringing.presetName(context, p)
                        PresetRow(
                            name = name,
                            detail = presetDetail(p),
                            removeLabel = if (p.ownId != null) {
                                if (armedId == p.ownId) stringResource(R.string.timer_remove_armed) else stringResource(R.string.timer_remove)
                            } else null,
                            onRemove = {
                                val id = p.ownId ?: return@PresetRow
                                if (armedId == id) {
                                    armedId = null
                                    scope.launch { db.presets().delete(id) }
                                } else armedId = id
                            },
                            onPick = {
                                BrewTimer.set(context, Brew(label = name, seconds = p.minSeconds, temp = p.temp))
                            },
                        )
                        DottedRule()
                    }
                    item(key = "save-own") {
                        TextMMD(
                            stringResource(R.string.timer_save_own),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { naming = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }

    if (naming) {
        NameDialog(
            initial = b.label,
            onDismiss = { naming = false },
            onSave = { name ->
                naming = false
                scope.launch {
                    db.presets().insert(
                        OwnPreset(
                            uuid = UUID.randomUUID().toString(),
                            name = name,
                            seconds = b.seconds,
                            tempF = b.temp?.f,
                            tempC = b.temp?.c,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                BrewTimer.set(context, b.copy(label = name))
            },
        )
    }
}

private fun OwnPreset.asPreset(): Preset = Preset(
    key = "own:$uuid",
    minSeconds = seconds,
    maxSeconds = seconds,
    temp = if (tempF != null && tempC != null) Temp(tempF, tempC) else null,
    ownName = name,
    ownId = id,
)

@Composable
private fun PresetRow(name: String, detail: String, removeLabel: String?, onRemove: () -> Unit, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).heightIn(min = 48.dp).clickable(onClick = onPick).padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            TextMMD(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            TextMMD(detail, style = MaterialTheme.typography.bodySmall)
        }
        if (removeLabel != null) {
            TextMMD(
                removeLabel,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onRemove).padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

/** "2 to 3 min · 175 °F (80 °C)", "4 min · 212 °F (100 °C)", "5 to 10 min". */
@Composable
fun presetDetail(p: Preset): String {
    val time = if (p.minSeconds == p.maxSeconds) {
        stringResource(R.string.timer_minutes_one, BrewMath.minutes(p.minSeconds))
    } else {
        stringResource(R.string.timer_minutes_range, BrewMath.minutes(p.minSeconds), BrewMath.minutes(p.maxSeconds))
    }
    return p.temp?.let { stringResource(R.string.timer_detail, time, tempText(it)) } ?: time
}

/** "175 °F (80 °C)": the pair as stored, °F first. */
@Composable
fun tempText(t: Temp): String = stringResource(R.string.prep_temp, t.f, t.c)

@Composable
private fun NameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(stringResource(R.string.timer_save_own_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TextFieldMMD(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            placeholder = { TextMMD(stringResource(R.string.timer_save_own_hint)) },
            singleLine = true,
        )
        DialogButtons(
            cancel = stringResource(R.string.dialog_cancel),
            confirm = stringResource(R.string.dialog_save),
            onCancel = onDismiss,
            onConfirm = { onSave(name.trim()) },
            confirmOn = true,
            confirmEnabled = name.isNotBlank(),
        )
    }
}

/** Something standing between the timer and its ring on time, and the screen that fixes it. */
data class TimerProblem(val text: Int, val button: Int, val open: Intent)

/** What would stop the timer ringing, or make it ring late (Parked's list, cut to two). */
fun timerProblems(context: Context): List<TimerProblem> = buildList {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        add(
            TimerProblem(
                R.string.timer_problem_notifications,
                R.string.timer_turn_on,
                Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
            ),
        )
    }
    if (!context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
        add(
            TimerProblem(
                R.string.timer_problem_exact,
                R.string.timer_allow,
                Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)),
            ),
        )
    }
}
