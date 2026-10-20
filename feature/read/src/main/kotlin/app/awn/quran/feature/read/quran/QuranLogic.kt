package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter

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
 * Decides whether the pager follows playback. The pager turns when the playing ayah is on another
 * page than the one shown and the reader was following: playback has just started, or the page
 * shown is the page the previous ayah was on. A reader who swiped elsewhere is left alone. Returns
 * the page to show, or null to stay.
 */
internal fun pageToFollow(previousPlayingPage: Int?, playingPage: Int, shownPage: Int): Int? {
    if (playingPage == shownPage) return null
    val wasFollowing = previousPlayingPage == null || previousPlayingPage == shownPage
    return if (wasFollowing) playingPage else null
}

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

/** What a volume key does on the Quran screen when the setting is on. */
internal enum class VolumeKeyAction {
    NextPage,
    PreviousPage,
}

/** Volume down turns to the next page and volume up to the previous one. */
internal fun volumeKeyAction(isVolumeDown: Boolean): VolumeKeyAction =
    if (isVolumeDown) VolumeKeyAction.NextPage else VolumeKeyAction.PreviousPage
