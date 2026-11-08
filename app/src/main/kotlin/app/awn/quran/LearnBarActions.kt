package app.awn.quran

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.components.AwnIconButton

/** The Learn tab's top-bar buttons: Settings, then the Learn with a teacher page. */
@Composable
internal fun LearnBarActions(navigation: TabNavigation) {
    AwnIconButton(
        icon = R.drawable.ic_app_settings,
        contentDescription = stringResource(R.string.index_settings),
        onClick = navigation::openSettings,
    )
    AwnIconButton(
        icon = R.drawable.ic_app_school,
        contentDescription = stringResource(R.string.notice_learn_title),
        onClick = navigation.openTeacherPage,
    )
}
