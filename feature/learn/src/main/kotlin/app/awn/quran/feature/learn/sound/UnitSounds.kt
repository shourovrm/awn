package app.awn.quran.feature.learn.sound

import androidx.compose.runtime.compositionLocalOf
import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.WordKey
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The voice of a Quran word, played by the app that hosts Learn. :app implements it with the word
 * reciter the user chose in Settings, so Learn does not know about reciters.
 */
fun interface WordSound {
    suspend fun play(word: WordKey)

    companion object {
        /** For hosts without word audio: the word is simply not played. */
        val Silent = WordSound {}
    }
}

const val NORMAL_SPEED = 1f

/** Plays one sound file and returns when it has ended. */
interface ClipPlayer {
    /** Plays [file] at [speed] times its normal speed. */
    suspend fun play(file: File, speed: Float = NORMAL_SPEED)

    fun stop()
}

/** What a unit screen can play: recorded clips and the audio of a Quran word. */
internal interface UnitSounds {

    /**
     * Plays [clipIds] one after another with no gap. Returns false when none of them could be
     * played because the sounds are not installed.
     */
    suspend fun playClips(clipIds: List<String>, speed: Float = NORMAL_SPEED): Boolean

    suspend fun playWord(word: WordKey)

    fun stop()

    companion object {
        /** For previews and tests of screens that have no sound. */
        val None =
            object : UnitSounds {
                override suspend fun playClips(clipIds: List<String>, speed: Float) = false

                override suspend fun playWord(word: WordKey) = Unit

                override fun stop() = Unit
            }
    }
}

internal val LocalUnitSounds = compositionLocalOf<UnitSounds> { UnitSounds.None }

/** Finds the clip files in the installed sounds and plays them with [clipPlayer]. */
internal class PackUnitSounds(
    private val packs: PackRepository,
    private val clipPlayer: ClipPlayer,
    private val wordSound: WordSound,
) : UnitSounds {

    override suspend fun playClips(clipIds: List<String>, speed: Float): Boolean {
        var hasPlayedAny = false
        for (clipId in clipIds) {
            val file = withContext(Dispatchers.IO) { packs.learnAudioFile(clipId) } ?: continue
            clipPlayer.play(file, speed)
            hasPlayedAny = true
        }
        return hasPlayedAny
    }

    override suspend fun playWord(word: WordKey) = wordSound.play(word)

    override fun stop() = clipPlayer.stop()
}
