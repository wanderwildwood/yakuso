package com.wanderwildwood.yakuso.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wanderwildwood.yakuso.R

/** For now one row: dark mode. The timer's and the journal's rows come with them. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Bar(stringResource(R.string.settings_title), onBack = onBack)
        Box(Modifier.weight(1f)) {
            PagedColumn(contentPadding = PaddingValues(start = 20.dp, end = 12.dp)) {
                item(key = "dark") {
                    SwitchRow(
                        title = stringResource(R.string.settings_dark),
                        checked = DarkMode.on,
                        onChange = { DarkMode.set(context, it) },
                    )
                }
            }
        }
    }
}
