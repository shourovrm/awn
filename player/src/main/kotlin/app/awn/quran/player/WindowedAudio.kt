package app.awn.quran.player

import app.awn.quran.core.data.AyahAudio
import app.awn.quran.core.data.WordTiming

/**
 * True when the bytes of this ayah can be fetched from its surah file as a small clip: the timing
 * pack placed the ayah inside a surah file and measured where its bytes lie and what time the first
 * of them has. Without [AyahAudio.startByteMs] the times of a clip could not be moved to its own
 * start, so such an ayah is treated like one with no byte data.
 */
internal val AyahAudio.canBeFetchedAsClip: Boolean
    get() = isInWholeSurahFile && startByte != null && startByteMs != null

/** The bytes of the surah file that make up this ayah's clip. */
internal fun AyahAudio.clipByteRange(): ByteRange =
    ByteRange(firstByte = checkNotNull(startByte), lastByte = endByte)

/**
 * True when the whole file plays from its first millisecond to its last, which is the only case in
 * which the engine can carry on into a prepared next file with no gap.
 */
internal val ResolvedAudio.playsWholeFile: Boolean
    get() = windowStartMs == 0L && windowEndMs == null

/**
 * How long the ayah plays, in milliseconds: from [ResolvedAudio.windowStartMs] to the end of the
 * window, or to the end of the file when the window has no end. [measuredFileLengthMs] is used when
 * neither the window nor the timing pack says where the file ends.
 */
internal fun ResolvedAudio.playedLengthMs(measuredFileLengthMs: Long): Long {
    val endMs = windowEndMs ?: durationMs ?: measuredFileLengthMs
    return (endMs - windowStartMs).coerceAtLeast(0L)
}

/**
 * Builds the audio of an ayah that lies inside a surah file ([AyahAudio.isInWholeSurahFile]).
 *
 * When the ayah's clip is on disk ([clipOnDisk]) its file starts at [AyahAudio.startByteMs] of the
 * surah file, so every time (window and word times) is moved back by that much. Otherwise the whole
 * surah file is streamed or read from disk and the times stay as the pack gives them, so the ayah
 * does not start at the beginning of the surah.
 */
internal fun windowedResolvedAudio(
    timed: AyahAudio,
    clipSource: AudioSource,
    surahSource: AudioSource,
    clipOnDisk: Boolean,
): ResolvedAudio {
    val readsClip = clipOnDisk && timed.canBeFetchedAsClip
    val clipStartMs = if (readsClip) checkNotNull(timed.startByteMs) else 0
    val endMs = timed.durationMs?.minus(clipStartMs)?.toLong()
    return ResolvedAudio(
        source = if (readsClip) clipSource else surahSource,
        durationMs = endMs,
        words = timed.words.map { it.movedBack(clipStartMs) },
        coversWholeSurah = false,
        windowStartMs = (timed.startMs - clipStartMs).toLong().coerceAtLeast(0L),
        windowEndMs = endMs,
    )
}

private fun WordTiming.movedBack(offsetMs: Int): WordTiming =
    if (offsetMs == 0) {
        this
    } else {
        copy(
            startMs = (startMs - offsetMs).coerceAtLeast(0),
            endMs = (endMs - offsetMs).coerceAtLeast(0),
        )
    }
