package app.awn.quran.feature.learn.sound

import app.awn.quran.core.data.downloads.PackTransfer

/** What the word page can do with the sound of one Quran word right now. */
sealed interface WordSoundStatus {
    /** The word reciter's timings are on the phone: a tap plays the word. */
    data object Ready : WordSoundStatus

    /** No word reciter has timings for this word: nothing to play and nothing to download. */
    data object Unavailable : WordSoundStatus

    /**
     * The timings are not on the phone. [packId] is the download to start on a tap, [downloadBytes]
     * its size, [progress] runs from 0 to 1 while it downloads and is null when none is queued, and
     * [hasFailed] is set when the last download stopped.
     */
    data class Missing(
        val packId: String,
        val downloadBytes: Long,
        val progress: Float?,
        val hasFailed: Boolean,
    ) : WordSoundStatus
}

/** The word timings pack of the reciter whose voice plays words, as the catalogue reports it. */
data class TimingPackState(
    val packId: String,
    val isInstalled: Boolean,
    val downloadBytes: Long,
    val transfer: PackTransfer?,
)

/**
 * Plays when the timings hold the word; otherwise offers the timings download when there is one
 * that is not installed. The download is never started here: the user taps for it.
 */
fun wordSoundStatusOf(hasTimings: Boolean, timingPack: TimingPackState?): WordSoundStatus =
    when {
        hasTimings -> WordSoundStatus.Ready
        // No word reciter, or the timings are installed but hold nothing for this word.
        timingPack == null || timingPack.isInstalled -> WordSoundStatus.Unavailable
        else -> missing(timingPack)
    }

private fun missing(timingPack: TimingPackState): WordSoundStatus.Missing {
    val progress =
        when (val transfer = timingPack.transfer) {
            PackTransfer.Waiting -> 0f
            is PackTransfer.Running -> transfer.progress
            else -> null
        }
    return WordSoundStatus.Missing(
        packId = timingPack.packId,
        downloadBytes = timingPack.downloadBytes,
        progress = progress,
        hasFailed = timingPack.transfer is PackTransfer.Failed,
    )
}
