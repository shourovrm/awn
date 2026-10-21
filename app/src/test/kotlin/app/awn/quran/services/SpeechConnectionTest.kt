package app.awn.quran.services

import android.app.Application
import android.content.ComponentName
import android.os.Binder
import android.os.Looper
import android.os.Parcel
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.tracker.PhonemeEvent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The app's side of the calls to the speech process, against a stand-in service binder. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeechConnectionTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val paths = ModelPaths(File("model.onnx"), File("tokens.txt"), File("reference.json"))
    private val heard = listOf(PhonemeEvent("b", 0.4f, -1f), PhonemeEvent("i", 0.5f, -1f))
    private val loadedModels = mutableListOf<String>()

    /** Answers like SpeechService: LOAD records the path, ACCEPT hears [heard], FINISH fails. */
    private val service =
        object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface(SpeechProtocol.DESCRIPTOR)
                val answer = checkNotNull(reply)
                when (code) {
                    SpeechProtocol.LOAD -> {
                        loadedModels += checkNotNull(data.readString())
                        answer.writeNoException()
                    }
                    SpeechProtocol.ACCEPT -> {
                        answer.writeNoException()
                        SpeechProtocol.writeEvents(answer, heard)
                    }
                    else -> answer.writeException(IllegalStateException("No speech stream 7."))
                }
                return true
            }
        }

    @Before
    fun registerService() {
        shadowOf(application)
            .setComponentNameAndServiceForBindService(
                ComponentName(application, SpeechService::class.java),
                service,
            )
    }

    private fun connected(): SpeechConnection {
        val connection = SpeechConnection.bind(application, paths)
        // Robolectric delivers onServiceConnected on the main looper.
        shadowOf(Looper.getMainLooper()).idle()
        return connection
    }

    @Test
    fun loadSendsTheModelPath() {
        connected().load()

        assertEquals(listOf("model.onnx"), loadedModels)
    }

    @Test
    fun theHeardPhonemesComeBackWhole() {
        val events =
            connected().call(SpeechProtocol.ACCEPT, { it.writeInt(1) }, SpeechProtocol::readEvents)

        assertEquals(heard, events)
    }

    @Test
    fun aFailureInsideTheServiceIsAnIllegalStateExceptionHere() {
        val connection = connected()

        val problem =
            assertThrows(IllegalStateException::class.java) {
                connection.call(SpeechProtocol.FINISH, { it.writeInt(7) }) {}
            }
        assertEquals("No speech stream 7.", problem.message)
    }

    @Test
    fun aCallAfterUnbindFailsAtOnceInsteadOfWaiting() {
        val connection = connected()
        connection.unbind()

        assertThrows(IllegalStateException::class.java) { connection.load() }
    }
}
