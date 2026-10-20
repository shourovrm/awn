package app.awn.quran.feature.listen

import app.awn.quran.core.data.AyahRef
import app.awn.quran.player.PlaybackOptions
import app.awn.quran.player.SleepTimer
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/** The repeat counts the panel offers and the bar steps through; the last one is "forever". */
internal val REPEAT_CHOICES = listOf(1, 2, 3, 5, 10, PlaybackOptions.INFINITE_REPEAT)

/** The speeds the bar's speed button steps through, starting from normal speed. */
internal val SPEED_CHOICES = listOf(1f, 1.25f, 1.5f, 0.75f)

/** The sleep timer choices of the play options, in menu order; null is "off". */
internal val SLEEP_TIMER_CHOICES: List<SleepTimer?> =
    listOf(
        null,
        SleepTimer.EndOfAyah,
        SleepTimer.EndOfSurah,
        SleepTimer.Minutes(15),
        SleepTimer.Minutes(30),
        SleepTimer.Minutes(60),
    )

private const val SPEED_TOLERANCE = 0.001f
private const val SPEED_DECIMALS = 2

/** Returns the repeat count after [current] in [REPEAT_CHOICES], wrapping to 1 after "forever". */
internal fun nextRepeat(current: Int): Int {
    val index = REPEAT_CHOICES.indexOf(current)
    return REPEAT_CHOICES[(index + 1) % REPEAT_CHOICES.size]
}

/** Returns the speed after [current] in [SPEED_CHOICES]; an unknown speed goes to normal. */
internal fun nextSpeed(current: Float): Float {
    val index = SPEED_CHOICES.indexOfFirst { abs(it - current) < SPEED_TOLERANCE }
    return SPEED_CHOICES[(index + 1) % SPEED_CHOICES.size]
}

/** Formats a speed with at most two decimals: 1.25, 1.5, 0.75, 1. */
fun formatSpeed(speed: Float): String =
    BigDecimal(speed.toDouble())
        .setScale(SPEED_DECIMALS, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

/**
 * Returns the ayahs a play button on a page can start from: the first ayah of the page for each
 * surah that has ayahs on it. A page with two surahs gives two, so the user can choose.
 */
internal fun startChoicesFor(ayahsOnPage: List<AyahRef>): List<AyahRef> =
    ayahsOnPage.sorted().distinctBy { it.surah }

/**
 * A from and to ayah of a range. Moving one end past the other drags the other end along, so [from]
 * never comes after [to].
 */
data class RangeSelection(val from: AyahRef, val to: AyahRef, val onlyRange: Boolean = true) {

    fun withFrom(newFrom: AyahRef) = copy(from = newFrom, to = maxOf(to, newFrom))

    fun withTo(newTo: AyahRef) = copy(to = newTo, from = minOf(from, newTo))
}
