package app.awn.quran.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.File

/**
 * Reads a downloaded copy of the audio when there is one and streams otherwise. The media item
 * carries the name of the downloaded copy as its custom cache key, which arrives here as
 * [DataSpec.key]; a key that does not name a file under [store] is streamed.
 */
@UnstableApi
class LocalFirstDataSource(
    private val store: LocalAudioStore,
    private val fileDataSource: DataSource,
    private val streamingDataSource: DataSource,
) : DataSource {

    private var activeDataSource: DataSource? = null
    private val transferListeners = mutableListOf<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
        fileDataSource.addTransferListener(transferListener)
        streamingDataSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val downloadedFile: File? =
            dataSpec.key?.let(store::fileForKey)?.takeIf { it.isFile && it.length() > 0L }
        return if (downloadedFile != null) {
            activeDataSource = fileDataSource
            fileDataSource.open(dataSpec.buildUpon().setUri(Uri.fromFile(downloadedFile)).build())
        } else {
            activeDataSource = streamingDataSource
            streamingDataSource.open(dataSpec)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(activeDataSource) { "read before open" }.read(buffer, offset, length)

    override fun getUri(): Uri? = activeDataSource?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        activeDataSource?.responseHeaders.orEmpty()

    override fun close() {
        activeDataSource?.close()
        activeDataSource = null
    }
}
