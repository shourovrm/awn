package app.awn.quran.notice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.AppPreferences
import kotlinx.coroutines.launch

/**
 * Shows the notice the first time the user opens Learn, and [learn] once it is accepted. Users who
 * accepted the older launch notice never see it again.
 */
@Composable
fun LearnNoticeGate(preferences: AppPreferences, learn: @Composable () -> Unit) {
    // null until DataStore answers, so the notice does not flash for users who accepted it.
    val accepted by preferences.noticeAccepted.collectAsStateWithLifecycle(initialValue = null)
    val coroutineScope = rememberCoroutineScope()
    when (accepted) {
        null -> Unit
        false -> NoticeScreen(onAccept = { coroutineScope.launch { preferences.acceptNotice() } })
        true -> learn()
    }
}
