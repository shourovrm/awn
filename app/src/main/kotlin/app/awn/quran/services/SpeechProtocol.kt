package app.awn.quran.services

import android.os.IBinder
import android.os.Parcel
import app.awn.quran.tracker.PhonemeEvent

/**
 * The calls between the app and [SpeechService], which runs the speech model in the `:speech`
 * process. Plain binder transactions: the app is the only caller and every call is synchronous,
 * like the in-process recogniser it replaces.
 */
internal object SpeechProtocol {
    const val DESCRIPTOR = "app.awn.quran.services.SpeechService"

    /** Model path and tokens path in; loads the model unless it is loaded. */
    const val LOAD = IBinder.FIRST_CALL_TRANSACTION

    /** Nothing in; the id of a new recognition stream out. */
    const val OPEN_STREAM = IBinder.FIRST_CALL_TRANSACTION + 1

    /** Stream id and 16 kHz samples in; the new phonemes out. */
    const val ACCEPT = IBinder.FIRST_CALL_TRANSACTION + 2

    /** Stream id in; the phonemes still pending at the end of the audio out. */
    const val FINISH = IBinder.FIRST_CALL_TRANSACTION + 3

    /** Stream id in; frees the stream. */
    const val CLOSE_STREAM = IBinder.FIRST_CALL_TRANSACTION + 4

    fun writeEvents(reply: Parcel, events: List<PhonemeEvent>) {
        reply.writeInt(events.size)
        for (event in events) {
            reply.writeString(event.symbol)
            reply.writeFloat(event.onsetSeconds)
            reply.writeFloat(event.probability)
        }
    }

    fun readEvents(reply: Parcel): List<PhonemeEvent> {
        val count = reply.readInt()
        return List(count) {
            PhonemeEvent(
                symbol = checkNotNull(reply.readString()) { "A phoneme without a symbol." },
                onsetSeconds = reply.readFloat(),
                probability = reply.readFloat(),
            )
        }
    }
}
