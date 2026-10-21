package app.awn.quran.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import app.awn.quran.feature.recite.PreparedSpeech
import app.awn.quran.feature.recite.SpeechPreparation
import app.awn.quran.tracker.JsonReferencePhonemes
import app.awn.quran.tracker.PhonemeEvent
import app.awn.quran.tracker.PhonemeSource
import app.awn.quran.tracker.ReferencePhonemes
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Speech preparation backed by [SpeechService] in the `:speech` process: the reference text is
 * parsed here, the model loads there. Releasing it unbinds, which ends that process and gives its
 * memory back to the system.
 */
internal object RemoteSpeech {

    // Binds and loads the model while the preparing thread parses the reference text.
    private val loadExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "awn-speech-load").apply { isDaemon = true }
    }

    fun preparation(context: Context, modelFiles: ModelFiles): SpeechPreparation =
        SpeechPreparation {
            val paths = modelFiles.ensureInstalled()
            // Starting the other process and loading the model take about 2.5 s; the 13 MB
            // reference text is parsed here meanwhile instead of after it.
            val remote =
                CompletableFuture.supplyAsync(
                    { bindAndLoad(context.applicationContext, paths) },
                    loadExecutor,
                )
            var reference: ReferencePhonemes? = null
            try {
                reference = JsonReferencePhonemes.fromFile(paths.referencePhonemes)
            } finally {
                // A failed parse leaves nobody to release the model the other thread loads.
                if (reference == null) remote.thenAccept(SpeechConnection::unbind)
            }
            val connection = awaitConnection(remote)
            object : PreparedSpeech {
                override val referencePhonemes = checkNotNull(reference)

                override fun newPhonemeSource(): PhonemeSource {
                    // Loads again only if Android restarted the process since (low memory).
                    connection.load()
                    return RemotePhonemeSource(connection)
                }

                override fun close() = connection.unbind()
            }
        }

    private fun bindAndLoad(context: Context, paths: ModelPaths): SpeechConnection {
        val connection = SpeechConnection.bind(context, paths)
        try {
            connection.load()
        } catch (problem: IllegalStateException) {
            connection.unbind()
            throw problem
        }
        return connection
    }

    // The preparation reports every failure as IllegalStateException ("can't use the microphone").
    private fun awaitConnection(remote: CompletableFuture<SpeechConnection>): SpeechConnection =
        try {
            remote.get()
        } catch (problem: ExecutionException) {
            throw IllegalStateException("The speech model could not be loaded.", problem)
        }
}

/**
 * One binding to [SpeechService]; every call blocks, so none may run on the main thread. When
 * Android kills the `:speech` process the binding stays and Android starts the service again; calls
 * wait for it, and [load] puts the model back.
 */
internal class SpeechConnection
private constructor(private val context: Context, private val paths: ModelPaths) :
    ServiceConnection {

    private val lock = ReentrantLock()
    private val binderChanged = lock.newCondition()
    private var binder: IBinder? = null
    private var isBound = true

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        lock.withLock {
            binder = service
            binderChanged.signalAll()
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        lock.withLock { binder = null }
    }

    /** Loads the model in the service; does nothing when it is loaded. */
    fun load() {
        call(
            SpeechProtocol.LOAD,
            { data ->
                data.writeString(paths.model.path)
                data.writeString(paths.tokens.path)
            },
        ) {}
    }

    /** Ends the binding; the service, and with it the `:speech` process, stops. */
    fun unbind() {
        lock.withLock {
            // Android throws on a second unbind; a failed load and a release can both get here.
            if (!isBound) return
            isBound = false
            binder = null
            binderChanged.signalAll()
        }
        context.unbindService(this)
    }

    /**
     * Sends [code] with what [writeArguments] writes and returns what [readAnswer] reads from the
     * reply. A dead process and an error inside the service both come out as
     * [IllegalStateException].
     */
    fun <T> call(code: Int, writeArguments: (Parcel) -> Unit, readAnswer: (Parcel) -> T): T {
        val target = awaitBinder()
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SpeechProtocol.DESCRIPTOR)
            writeArguments(data)
            target.transact(code, data, reply, 0)
            reply.readException()
            return readAnswer(reply)
        } catch (problem: RemoteException) {
            throw IllegalStateException("The speech process has stopped.", problem)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun awaitBinder(): IBinder = lock.withLock {
        var waitNanos = TimeUnit.SECONDS.toNanos(CONNECT_TIMEOUT_SECONDS)
        while (binder == null && isBound && waitNanos > 0) {
            waitNanos = binderChanged.awaitNanos(waitNanos)
        }
        checkNotNull(binder) { "The speech service is not running." }
    }

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 10L

        /** Starts the binding; the first [call] waits until the service answers. */
        fun bind(context: Context, paths: ModelPaths): SpeechConnection {
            val connection = SpeechConnection(context, paths)
            val intent = Intent(context, SpeechService::class.java)
            check(context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                "The speech service could not be started."
            }
            return connection
        }
    }
}

/** One recitation's stream in the `:speech` process. */
private class RemotePhonemeSource(private val connection: SpeechConnection) : PhonemeSource {
    private val streamId = connection.call(SpeechProtocol.OPEN_STREAM, {}) { it.readInt() }

    override fun accept(block: FloatArray): List<PhonemeEvent> =
        connection.call(
            SpeechProtocol.ACCEPT,
            { data ->
                data.writeInt(streamId)
                data.writeFloatArray(block)
            },
            SpeechProtocol::readEvents,
        )

    override fun finish(): List<PhonemeEvent> =
        connection.call(
            SpeechProtocol.FINISH,
            { it.writeInt(streamId) },
            SpeechProtocol::readEvents,
        )

    override fun close() {
        try {
            connection.call(SpeechProtocol.CLOSE_STREAM, { it.writeInt(streamId) }) {}
        } catch (problem: IllegalStateException) {
            // The process is gone and its stream with it; there is nothing left to free.
            Log.w(TAG, "Speech stream $streamId was not closed", problem)
        }
    }

    private companion object {
        const val TAG = "RemoteSpeech"
    }
}
