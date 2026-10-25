package app.awn.quran

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.core.ui.components.KeepScreenOn
import app.awn.quran.services.AwnServices
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What lasts as long as the Quran screen is shown: the screen stays lit when the user chose so, and
 * an audio request that has not started to sound is dropped when the user leaves.
 */
@Composable
internal fun QuranRouteEffects(services: AwnServices) {
    val keepScreenOnFlow =
        remember(services) {
            services.readDependencies.settingsStore.settings
                .map { it.keepScreenOn }
                .distinctUntilChanged()
        }
    val keepScreenOn by keepScreenOnFlow.collectAsStateWithLifecycle(initialValue = true)
    KeepScreenOn(enabled = keepScreenOn)

    val activity = LocalActivity.current
    DisposableEffect(services) {
        onDispose {
            // Turning the phone rebuilds the screen; the request should survive that.
            if (activity?.isChangingConfigurations != true) services.audio.player.cancelPending()
        }
    }
}
