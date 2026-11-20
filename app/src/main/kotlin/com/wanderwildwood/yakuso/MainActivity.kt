package com.wanderwildwood.yakuso

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.yakuso.herbs.Herb
import com.wanderwildwood.yakuso.herbs.HerbBook
import com.wanderwildwood.yakuso.ui.AboutDialog
import com.wanderwildwood.yakuso.ui.ArmedScope
import com.wanderwildwood.yakuso.ui.DarkSelection
import com.wanderwildwood.yakuso.ui.HerbScreen
import com.wanderwildwood.yakuso.ui.HerbsScreen
import com.wanderwildwood.yakuso.ui.SettingsScreen
import com.wanderwildwood.yakuso.ui.houseTypography
import com.wanderwildwood.yakuso.ui.monochrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                            herbs?.let { Herbal(it) }
                        }
                    }
                }
            } }
        }
    }
}

/**
 * The list, one herb, and Settings. No navigation library: the herb being read and whether
 * Settings is open decide what is shown, and Back steps out of each.
 */
@Composable
private fun Herbal(herbs: List<Herb>) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    val open = openId?.let { id -> herbs.firstOrNull { it.id == id } }

    BackHandler(enabled = open != null || settings) {
        when {
            settings -> settings = false
            else -> openId = null
        }
    }

    when {
        settings -> SettingsScreen(onBack = { settings = false })
        open != null -> HerbScreen(open, onBack = { openId = null })
        else -> HerbsScreen(
            herbs = herbs,
            onOpen = { openId = it.id },
            onSettings = { settings = true },
            onAbout = { about = true },
        )
    }

    if (about) AboutDialog(onDismiss = { about = false })
}
