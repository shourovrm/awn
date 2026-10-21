package app.awn.quran.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import app.awn.quran.tracker.PhonemeSource
import app.awn.quran.tracker.SherpaRecognizer

/**
 * Runs the speech model in the `:speech` process. In the app's own process the native allocator and
 * the mapped speech library kept about 55 MB after the model was released (measured with `dumpsys
 * meminfo` after a 30 s recitation); a process of its own gives all of it back when it ends. The
 * process ends as soon as the app unbinds (see [onDestroy]).
 */
class SpeechService : Service() {

    private val lock = Any()
    private var recognizer: SherpaRecognizer? = null
    private val streams = mutableMapOf<Int, PhonemeSource>()
    private var nextStreamId = 1

    private val binder =
        object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code !in SpeechProtocol.LOAD..SpeechProtocol.CLOSE_STREAM) {
                    return super.onTransact(code, data, reply, flags)
                }
                data.enforceInterface(SpeechProtocol.DESCRIPTOR)
                val answer = checkNotNull(reply) { "Speech calls are never one-way." }
                // The app reads these back as the same exception types (Parcel.readException).
                try {
                    synchronized(lock) { handle(code, data, answer) }
                } catch (problem: IllegalStateException) {
                    answer.writeException(problem)
                } catch (problem: IllegalArgumentException) {
                    answer.writeException(problem)
                }
                return true
            }
        }

    override fun onBind(intent: Intent): IBinder = binder

    // Must be called with [lock] held.
    private fun handle(code: Int, data: Parcel, reply: Parcel) {
        when (code) {
            SpeechProtocol.LOAD -> {
                val modelPath = checkNotNull(data.readString()) { "No model path." }
                val tokensPath = checkNotNull(data.readString()) { "No tokens path." }
                if (recognizer == null) recognizer = loadRecognizer(modelPath, tokensPath)
                reply.writeNoException()
            }
            SpeechProtocol.OPEN_STREAM -> {
                val loaded = checkNotNull(recognizer) { "The speech model is not loaded." }
                val id = nextStreamId++
                streams[id] = loaded.newSource()
                reply.writeNoException()
                reply.writeInt(id)
            }
            SpeechProtocol.ACCEPT -> {
                val stream = streamOf(data.readInt())
                val samples = checkNotNull(data.createFloatArray()) { "No samples." }
                val events = stream.accept(samples)
                reply.writeNoException()
                SpeechProtocol.writeEvents(reply, events)
            }
            SpeechProtocol.FINISH -> {
                val events = streamOf(data.readInt()).finish()
                reply.writeNoException()
                SpeechProtocol.writeEvents(reply, events)
            }
            SpeechProtocol.CLOSE_STREAM -> {
                streams.remove(data.readInt())?.close()
                reply.writeNoException()
            }
        }
    }

    private fun streamOf(id: Int): PhonemeSource =
        checkNotNull(streams[id]) { "No speech stream $id." }

    private fun loadRecognizer(modelPath: String, tokensPath: String): SherpaRecognizer =
        try {
            SherpaRecognizer(modelPath, tokensPath)
        } catch (problem: UnsatisfiedLinkError) {
            // The app shows IllegalStateException as "can't use the microphone".
            throw IllegalStateException("The speech library could not be loaded.", problem)
        }

    override fun onDestroy() {
        synchronized(lock) {
            streams.values.forEach { it.close() }
            streams.clear()
            recognizer?.close()
            recognizer = null
        }
        super.onDestroy()
        // A cached empty process would keep the speech library mapped and the allocator's pages;
        // this process exists only for the model, so it ends with the service.
        Process.killProcess(Process.myPid())
    }
}
