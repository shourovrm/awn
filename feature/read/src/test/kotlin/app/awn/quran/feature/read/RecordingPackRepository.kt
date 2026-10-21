package app.awn.quran.feature.read

import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.fakes.FakePackRepository
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** One call of [PackRepository.fontFile]: what was asked and the thread that asked. */
data class FontFileRequest(val script: ScriptId, val page: Int, val thread: Thread)

/**
 * A pack repository that notes every font file lookup. The lookup is where the font cache starts
 * reading files, so the notes show on which thread that work happens.
 */
class RecordingPackRepository(private val packs: FakePackRepository = FakePackRepository()) :
    PackRepository by packs {
    val fontFileRequests = CopyOnWriteArrayList<FontFileRequest>()

    fun install(script: ScriptId, file: File) {
        packs.fontFiles[script] = file
    }

    override fun fontFile(script: ScriptId, page: Int): File? {
        fontFileRequests += FontFileRequest(script, page, Thread.currentThread())
        return packs.fontFile(script, page)
    }
}
