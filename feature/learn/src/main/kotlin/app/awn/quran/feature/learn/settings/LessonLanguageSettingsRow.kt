package app.awn.quran.feature.learn.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.components.AwnChoiceRow
import app.awn.quran.core.ui.components.SettingRowKeys
import app.awn.quran.feature.learn.LearnServices
import app.awn.quran.feature.learn.R
import app.awn.quran.feature.learn.content.LessonLanguageChoice
import app.awn.quran.feature.learn.progress.LearnSettings
import kotlinx.coroutines.launch

/** The "Lesson language" row for the Settings hub, backed by Learn's own settings. */
@Composable
fun LessonLanguageSettingsRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { LearnServices.get(context).settings }
    LessonLanguageRow(settings, modifier)
}

@Composable
internal fun LessonLanguageRow(settings: LearnSettings, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val choice by settings.lessonLanguage.collectAsState(initial = LessonLanguageChoice.FollowApp)
    AwnChoiceRow(
        label = stringResource(R.string.lesson_language),
        options = LessonLanguageChoice.entries,
        selected = choice,
        optionLabel = { stringResource(choiceLabel(it)) },
        onSelect = { picked -> scope.launch { settings.setLessonLanguage(picked) } },
        modifier = modifier,
        rowKey = SettingRowKeys.RECITE_LESSON_LANGUAGE,
    )
}

private fun choiceLabel(choice: LessonLanguageChoice): Int =
    when (choice) {
        LessonLanguageChoice.FollowApp -> R.string.lesson_language_follow_app
        LessonLanguageChoice.English -> R.string.lesson_language_english
        LessonLanguageChoice.Bangla -> R.string.lesson_language_bangla
    }
