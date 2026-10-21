package app.awn.quran

import androidx.compose.runtime.Composable
import app.awn.quran.feature.hifz.HifzBar
import app.awn.quran.services.AwnServices

/**
 * The Quran screen's `hifzBar` slot: the Hifz practice bar while a practice is open, otherwise null
 * so the recite and audio bars show as usual. The speech download card still comes first.
 */
internal fun hifzBarSlot(
    services: AwnServices,
    isPracticing: Boolean,
): (@Composable (onExit: () -> Unit) -> Unit)? {
    if (!isPracticing) return null
    return { onExit ->
        ReciteBarOrDownload(services, onExit) {
            HifzBar(
                controller = services.hifz.controller,
                recite = services.recite,
                playRange = { first, last -> services.audio.controller.playRange(first, last) },
                onExit = onExit,
            )
        }
    }
}
