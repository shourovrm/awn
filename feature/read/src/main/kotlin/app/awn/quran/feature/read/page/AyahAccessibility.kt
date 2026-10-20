package app.awn.quran.feature.read.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.IntOffset
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.LineItem
import app.awn.quran.core.data.PageLayout
import app.awn.quran.feature.read.R
import app.awn.quran.feature.read.localName
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * How long after a page appears its screen-reader layer is built. The layer is only needed once the
 * reader starts exploring, so building it later keeps the first frame and the swipe light.
 */
internal const val ACCESSIBILITY_LAYER_DELAY_MS = 500L

/** The Unicode text of one ayah as far as it is on a page; what a screen reader speaks. */
internal data class SpokenAyah(val ayah: AyahRef, val arabic: String)

/**
 * The ayahs of [layout] in reading order, each with the Unicode text of its words. Glyph-font pages
 * draw private-use codes that a screen reader would spell out as nothing or as boxes, so the spoken
 * text always comes from `Word.textUthmani`, never from what is drawn.
 */
internal fun spokenAyahsOf(layout: PageLayout): List<SpokenAyah> {
    val textByAyah = linkedMapOf<AyahRef, StringBuilder>()
    val words = layout.lines.flatMap { it.items }.filterIsInstance<LineItem.WordItem>()
    for (item in words) {
        val key = item.word.key
        val text = normalizeFallbackText(item.word.textUthmani)
        if (text.isNotEmpty()) {
            val builder = textByAyah.getOrPut(AyahRef(key.surah, key.ayah)) { StringBuilder() }
            if (builder.isNotEmpty()) builder.append(' ')
            builder.append(text)
        }
    }
    return textByAyah.map { (ayah, builder) -> SpokenAyah(ayah, builder.toString()) }
}

/** Hides what is drawn from screen readers; [AyahTextLayer] speaks it instead. */
internal fun Modifier.hiddenFromScreenReaders(): Modifier = semantics { invisibleToUser() }

/**
 * One invisible node per ayah over the page, with "Al-Baqarah, ayah 255" and the Arabic text, in
 * reading order, and a long-click action that selects the ayah (the screen-reader form of a long
 * press). The layer appears [ACCESSIBILITY_LAYER_DELAY_MS] after the page does.
 */
@Composable
internal fun AyahTextLayer(
    layout: PageLayout,
    geometry: PageGeometry,
    chapters: List<Chapter>,
    onSelect: (AyahRef) -> Unit,
) {
    val isReady by
        produceState(initialValue = false, layout) {
            delay(ACCESSIBILITY_LAYER_DELAY_MS)
            value = true
        }
    if (!isReady) return
    val spoken = remember(layout) { spokenAyahsOf(layout) }
    val density = LocalDensity.current
    val selectLabel = stringResource(R.string.ayah_select_action)
    Box(Modifier.semantics { isTraversalGroup = true }) {
        spoken.forEachIndexed { index, entry ->
            val bounds = geometry.ayahBounds(entry.ayah) ?: return@forEachIndexed
            val chapterName =
                chapters.firstOrNull { it.number == entry.ayah.surah }?.localName().orEmpty()
            val reference = stringResource(R.string.ayah_spoken, chapterName, entry.ayah.ayah)
            val description =
                stringResource(R.string.ayah_spoken_with_text, reference, entry.arabic)
            Box(
                Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .size(
                        with(density) { bounds.width.toDp() },
                        with(density) { bounds.height.toDp() },
                    )
                    .semantics {
                        contentDescription = description
                        traversalIndex = index.toFloat()
                        onLongClick(label = selectLabel) {
                            onSelect(entry.ayah)
                            true
                        }
                    },
            )
        }
    }
}
