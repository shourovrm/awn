package app.awn.quran.feature.read.quran

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.ui.theme.AwnSpacing

/** Where the Quran screen opens: a page, or an ayah that is also selected. */
sealed interface QuranTarget {
    data class Page(val page: Int) : QuranTarget

    data class Ayah(val ayah: AyahRef) : QuranTarget
}

/**
 * The ayah [delta] places after (positive) or before (negative) [ayah] in reading order, crossing
 * from the last ayah of a surah to the first of the next. Null at the first and last ayah of the
 * Quran.
 */
@Suppress("ReturnCount") // Each early return is one edge of the Quran or of a surah.
internal fun adjacentAyah(ayah: AyahRef, delta: Int, chapters: List<Chapter>): AyahRef? {
    require(delta == 1 || delta == -1) { "Only the next or previous ayah is supported." }
    val chapter = chapters.firstOrNull { it.number == ayah.surah } ?: return null
    if (delta == 1) {
        if (ayah.ayah < chapter.versesCount) return AyahRef(ayah.surah, ayah.ayah + 1)
        val following = chapters.firstOrNull { it.number == ayah.surah + 1 } ?: return null
        return AyahRef(following.number, 1)
    }
    if (ayah.ayah > 1) return AyahRef(ayah.surah, ayah.ayah - 1)
    val preceding = chapters.firstOrNull { it.number == ayah.surah - 1 } ?: return null
    return AyahRef(preceding.number, preceding.versesCount)
}

/** The text that copy and share send: "Arabic — translation (2:255)". */
internal fun ayahShareText(arabic: String, translation: String?, ayah: AyahRef): String =
    if (translation.isNullOrBlank()) {
        "$arabic ($ayah)"
    } else {
        "$arabic — $translation ($ayah)"
    }

/**
 * Whether the pager turns to the playing ayah's page: the reader is following the audio and is on
 * another page than the one the ayah is on.
 */
internal fun shouldTurnToPlayingPage(isFollowing: Boolean, playingPage: Int, shownPage: Int) =
    isFollowing && playingPage != shownPage

/**
 * The following switch after the reader arrives on [shownPage]. Arriving on the playing page
 * (swiping back to it, or the page turn that follows the audio) follows; arriving anywhere else
 * while audio plays is the reader's own choice and stops following. With no audio it is unchanged.
 */
internal fun isFollowingOn(wasFollowing: Boolean, playingPage: Int?, shownPage: Int): Boolean =
    if (playingPage == null) wasFollowing else shownPage == playingPage

/**
 * How far a page taller than its window must be scrolled up so the span [spanTop]..[spanBottom] is
 * inside a window of [windowHeight], with [margin] of room. The top of the span wins when it does
 * not fit. All values are in pixels; the result is never negative.
 */
internal fun scrollToKeepVisible(
    spanTop: Float,
    spanBottom: Float,
    windowHeight: Float,
    margin: Float,
): Float {
    val toShowBottom = spanBottom + margin - windowHeight
    val mostBeforeTopIsHidden = spanTop - margin
    return minOf(toShowBottom, mostBeforeTopIsHidden).coerceAtLeast(0f)
}

/**
 * How far the Mushaf page slides up so a selection on its last lines is not under the audio bar,
 * which covers the bottom [clearance] of a page [pageHeight] tall. The slide never exceeds the bar.
 */
internal fun shiftToClearAudioBar(
    spanTop: Float,
    spanBottom: Float,
    pageHeight: Float,
    clearance: Float,
    margin: Float,
): Float =
    scrollToKeepVisible(spanTop, spanBottom, pageHeight - clearance, margin).coerceAtMost(clearance)

/** What a volume key does on the Quran screen when the setting is on. */
internal enum class VolumeKeyAction {
    NextPage,
    PreviousPage,
}

/**
 * Volume down turns to the next page and volume up to the previous one, but only while no audio is
 * playing, paused or downloading: then the keys belong to the volume. Null means the keys are left
 * to the system.
 */
internal fun volumeKeyAction(isVolumeDown: Boolean, isAudioIdle: Boolean): VolumeKeyAction? =
    when {
        !isAudioIdle -> null
        isVolumeDown -> VolumeKeyAction.NextPage
        else -> VolumeKeyAction.PreviousPage
    }

/** The share of a list's height left above an ayah that the list scrolls to. */
internal const val TOP_MARGIN_SHARE = 0.05f

/** The room, in pixels, left above a scrolled-to ayah in a list [visibleHeight] pixels tall. */
internal fun topMarginFor(visibleHeight: Int): Int = (visibleHeight * TOP_MARGIN_SHARE).toInt()

/** Whether an item at [offset] with [size] lies wholly between [visibleTop] and [visibleBottom]. */
internal fun isFullyVisible(offset: Int, size: Int, visibleTop: Int, visibleBottom: Int): Boolean =
    offset >= visibleTop && offset + size <= visibleBottom

/**
 * The padding of a list page. Below, it clears the navigation bar and either the audio bar's fixed
 * height or, while reciting, the recite bar as it was measured ([reciteBarHeight]): that bar is two
 * or three rows tall, so a fixed height left the last ayahs under it.
 */
internal fun listContentPadding(reciteBarHeight: Dp?, navigationBarHeight: Dp): PaddingValues =
    PaddingValues(
        start = AwnSpacing.screenMargin,
        end = AwnSpacing.screenMargin,
        top = AwnSpacing.huge + AwnSpacing.small,
        bottom =
            if (reciteBarHeight != null) {
                reciteBarHeight + AwnSpacing.small
            } else {
                AwnSpacing.dockHeight + AwnSpacing.small + navigationBarHeight
            },
    )
