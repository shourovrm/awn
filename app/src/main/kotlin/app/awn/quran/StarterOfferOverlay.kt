package app.awn.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.feature.read.quran.StarterOfferCard
import app.awn.quran.services.AwnServices
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The first-launch card that offers a translation and word-by-word meanings, drawn over the Quran
 * screen. It waits until the print card has been handled, so only one card shows at a time, and it
 * is gone for good once the user has answered it.
 */
@Composable
internal fun StarterOfferOverlay(services: AwnServices) {
    val starterLanguage by
        remember(services) {
                combine(
                    services.preferences.starterOfferLanguage,
                    services.preferences.printOfferPending,
                ) { language, isPrintOfferPending ->
                    language.takeUnless { isPrintOfferPending }
                }
            }
            .collectAsStateWithLifecycle(initialValue = null)
    val coroutineScope = rememberCoroutineScope()
    starterLanguage?.let { languageTag ->
        StarterOfferCard(
            dependencies = services.readDependencies,
            languageTag = languageTag,
            onHandled = {
                coroutineScope.launch { services.preferences.markStarterOfferHandled() }
            },
        )
    }
}
