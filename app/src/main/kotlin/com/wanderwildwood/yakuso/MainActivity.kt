package com.wanderwildwood.yakuso

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.yakuso.data.HerbalDatabase
import com.wanderwildwood.yakuso.herbs.Herb
import com.wanderwildwood.yakuso.herbs.HerbBook
import com.wanderwildwood.yakuso.timer.BrewMath
import com.wanderwildwood.yakuso.timer.BrewTimer
import com.wanderwildwood.yakuso.timer.Ringing
import com.wanderwildwood.yakuso.ui.AboutDialog
import com.wanderwildwood.yakuso.ui.ArmedScope
import com.wanderwildwood.yakuso.ui.CupboardItemScreen
import com.wanderwildwood.yakuso.ui.CupboardScreen
import com.wanderwildwood.yakuso.ui.DarkSelection
import com.wanderwildwood.yakuso.ui.Door
import com.wanderwildwood.yakuso.ui.FinderResults
import com.wanderwildwood.yakuso.ui.FinderScreen
import com.wanderwildwood.yakuso.ui.HerbScreen
import com.wanderwildwood.yakuso.ui.HerbsScreen
import com.wanderwildwood.yakuso.ui.JournalEntryScreen
import com.wanderwildwood.yakuso.ui.JournalScreen
import com.wanderwildwood.yakuso.ui.SettingsScreen
import com.wanderwildwood.yakuso.ui.TimerScreen
import com.wanderwildwood.yakuso.ui.houseTypography
import com.wanderwildwood.yakuso.ui.monochrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /** A screen asked for from outside: the timer's notification opens the timer. */
    private val asked = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) take(intent)
        setContent {
            ThemeMMD(colorScheme = monochrome, typography = houseTypography) { DarkSelection {
                ArmedScope {
                    // A blinking caret repaints the e-ink panel twice a second for nothing.
                    CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                            // The book is read off the main thread, so the blank page is drawn at once.
                            val herbs by produceState<List<Herb>?>(null) {
                                value = withContext(Dispatchers.Default) { HerbBook.get(applicationContext.assets) }
                            }
                            val want by asked.collectAsState()
                            herbs?.let { Herbal(it, want) { asked.value = null } }
                        }
                    }
                }
            } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    override fun onResume() {
        super.onResume()
        // An alarm dropped by a force-stop or an update is set again; one already due rings.
        BrewTimer.resync(this)
    }

    private fun take(intent: Intent?) {
        intent?.getStringExtra(Ringing.EXTRA_SCREEN)?.let { asked.value = it }
    }
}

/**
 * Where the reader is, as a stack of plain words, so it survives the activity being remade:
 * "herb:<id>", "finder", "finder:<need>", "timer", "journal", "journal:<id>", "journal:new",
 * "journal:brew", "cupboard", "cupboard:<id>", "cupboard:new", "settings". The Herbs list is
 * under all of them. Back takes the top one off. No navigation library.
 */
@Composable
private fun Herbal(herbs: List<Herb>, asked: String?, onAsked: () -> Unit) {
    val context = LocalContext.current
    val stack: SnapshotStateList<String> = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf<String>() }
    var about by rememberSaveable { mutableStateOf(false) }
    val db = remember { HerbalDatabase.get(context) }
    val cupboard by remember(db) { db.cupboard().all() }.collectAsState(initial = emptyList())
    val inCupboard = remember(cupboard) { cupboard.mapNotNull { it.herbId }.toSet() }

    fun push(s: String) { if (stack.lastOrNull() != s) stack.add(s) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    LaunchedEffect(asked) {
        if (asked == Ringing.SCREEN_TIMER) push("timer")
        if (asked != null) onAsked()
    }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    val top = stack.lastOrNull()
    val (kind, arg) = top?.split(":", limit = 2)?.let { it[0] to it.getOrNull(1) } ?: (null to null)
    when (kind) {
        "settings" -> SettingsScreen(onBack = { pop() })
        "herb" -> {
            val herb = herbs.firstOrNull { it.id == arg }
            // An herb no longer in the book (a saved place from an older version): step back.
            if (herb == null) LaunchedEffect(top) { pop() } else HerbScreen(
                herb = herb,
                cupboard = cupboard.firstOrNull { it.herbId == herb.id },
                onBrew = {
                    BrewMath.fromHerb(herb)?.let { BrewTimer.set(context, it) }
                    push("timer")
                },
                onBack = { pop() },
            )
        }
        "finder" -> if (arg == null) {
            FinderScreen(onNeed = { push("finder:$it") }, onBack = { pop() })
        } else {
            FinderResults(arg, herbs, inCupboard, onOpen = { push("herb:${it.id}") }, onBack = { pop() })
        }
        "timer" -> TimerScreen(
            onAddToJournal = { pop(); push("journal"); push("journal:brew") },
            onBack = { pop() },
        )
        "journal" -> when (arg) {
            null -> JournalScreen(onOpen = { push("journal:$it") }, onNew = { push("journal:new") }, onBack = { pop() })
            else -> androidx.compose.runtime.key(top) {
                JournalEntryScreen(id = arg.toLongOrNull(), fromBrew = arg == "brew", herbs = herbs, onDone = { pop() })
            }
        }
        "cupboard" -> when (arg) {
            null -> CupboardScreen(onOpen = { push("cupboard:$it") }, onNew = { push("cupboard:new") }, onBack = { pop() })
            else -> androidx.compose.runtime.key(top) {
                CupboardItemScreen(id = arg.toLongOrNull(), herbs = herbs, onDone = { pop() })
            }
        }
        else -> HerbsScreen(
            herbs = herbs,
            cupboard = inCupboard,
            onOpen = { push("herb:${it.id}") },
            onDoor = { d ->
                push(
                    when (d) {
                        Door.FINDER -> "finder"
                        Door.TIMER -> "timer"
                        Door.JOURNAL -> "journal"
                        Door.CUPBOARD -> "cupboard"
                    },
                )
            },
            onSettings = { push("settings") },
            onAbout = { about = true },
        )
    }

    if (about) AboutDialog(onDismiss = { about = false })
}
