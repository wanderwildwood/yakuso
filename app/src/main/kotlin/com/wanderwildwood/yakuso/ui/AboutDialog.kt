package com.wanderwildwood.yakuso.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.yakuso.BuildConfig
import com.wanderwildwood.yakuso.R

/**
 * This app's About: only the words and the address. The dialog, its drawers, its one Close button,
 * the Licenses drawer, the links and the llama are the shared HouseAbout.kt, the same in every app.
 *
 * "Not medical advice" has its own heading and comes straight after what the app is; where the
 * herb text comes from follows it, so a stranger can see what the book stands on.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val data = AboutData(
        title = stringResource(R.string.about_title, BuildConfig.VERSION_NAME),
        sections = buildList {
            add(AboutSection(null, stringResource(R.string.about_what_it_is)))
            add(AboutSection(stringResource(R.string.about_safety_heading), stringResource(R.string.safety_disclaimer)))
            add(AboutSection(stringResource(R.string.about_sources_heading), stringResource(R.string.about_sources)))
            add(AboutSection(stringResource(R.string.about_privacy_heading), stringResource(R.string.about_privacy)))
            add(AboutSection(null, stringResource(R.string.about_privacy_close)))
            add(AboutSection(stringResource(R.string.privacy_who_title), stringResource(R.string.privacy_who)))
            add(AboutSection(stringResource(R.string.privacy_contact_title), stringResource(R.string.privacy_contact)))
            add(AboutSection(stringResource(R.string.privacy_rights_title), stringResource(R.string.privacy_rights)))
            add(AboutSection(stringResource(R.string.about_terms_heading), stringResource(R.string.about_terms_licence)))
            add(AboutSection(null, stringResource(R.string.about_terms)))
        },
        sourceUrl = SOURCE_URL,
    )
    val labels = AboutLabels(
        close = stringResource(R.string.about_close),
        licences = stringResource(R.string.licences_title),
        feedTheLlamas = stringResource(R.string.about_feed_the_llamas),
        noBrowser = { address -> context.getString(R.string.about_no_browser_address, address) },
        writeTo = { address -> context.getString(R.string.about_write_to, address) },
        noMailApp = { address -> context.getString(R.string.about_no_mail_app, address) },
        openDrawer = { heading -> context.getString(R.string.about_open_drawer, heading) },
        closeDrawer = { heading -> context.getString(R.string.about_close_drawer, heading) },
        intro = stringResource(R.string.about_intro_head),
        contact = stringResource(R.string.about_contact_head),
        links = stringResource(R.string.about_links_head),
    )
    HouseAboutDialog(data, labels, R.drawable.llama, onDismiss)
}
