package app.awn.quran

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.feature.hifz.HifzBar
import app.awn.quran.feature.recite.ReciteFocusedWordCard
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
            // A tap on a yellow or red word focuses it; this bar has no review button, so the
            // card for that word is drawn here, above the bar.
            Column {
                ReciteFocusedWordCard(
                    services.recite,
                    Modifier.padding(horizontal = AwnSpacing.dockFloatGap),
                )
                HifzBar(
                    controller = services.hifz.controller,
                    recite = services.recite,
                    playRange = { first, last -> services.audio.controller.playRange(first, last) },
                    onExit = onExit,
                )
            }
        }
    }
}
