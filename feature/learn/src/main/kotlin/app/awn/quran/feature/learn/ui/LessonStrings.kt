package app.awn.quran.feature.learn.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * The string [id] in the lesson language, which is not always the app language: a user whose app is
 * in English can read the lessons in Bangla, and the words drawn into a lesson (the labels of the
 * mouth drawing) must match the lesson text around them.
 */
@Composable
internal fun lessonString(@StringRes id: Int): String {
    val context = LocalContext.current
    val languageCode = LocalLearnEnvironment.current?.languageCode
    val lessonContext = remember(context, languageCode) { contextInLanguage(context, languageCode) }
    return lessonContext.getString(id)
}

/** [context] with its resources in [languageCode]; [context] itself when the code is null. */
// The app bundle keeps every language in the base APK (app/build.gradle.kts: bundle { language {
// enableSplit = false } }), so no language download is needed; lint cannot see that from here.
@SuppressLint("AppBundleLocaleChanges")
internal fun contextInLanguage(context: Context, languageCode: String?): Context {
    if (languageCode == null) return context
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocale(Locale.forLanguageTag(languageCode))
    return context.createConfigurationContext(configuration)
}
