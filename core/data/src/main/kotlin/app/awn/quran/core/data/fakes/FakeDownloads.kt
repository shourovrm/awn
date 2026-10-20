package app.awn.quran.core.data.fakes

import app.awn.quran.core.data.PackRepository
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.core.data.downloads.PackWorkScheduler
import app.awn.quran.core.data.downloads.WantedPackStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

/** A wanted list in memory, for tests. */
class InMemoryWantedPackStore(initial: Set<String> = emptySet()) : WantedPackStore {
    private val stored = initial.toMutableSet()

    override fun ids(): Set<String> = stored.toSet()

    override fun add(id: String) {
        stored += id
    }

    override fun remove(id: String) {
        stored -= id
    }
}

/**
 * A scheduler that only records what it is asked to do. Tests set [transferStates] to show a
 * progress state and read [enqueued], [cancelled] and [pruneCount] to check the calls.
 */
class RecordingPackWorkScheduler : PackWorkScheduler {
    val transferStates = MutableStateFlow<Map<String, PackTransfer>>(emptyMap())
    val enqueued = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    var pruneCount = 0
        private set

    override val transfers: Flow<Map<String, PackTransfer>> = transferStates

    override fun enqueue(id: String) {
        enqueued += id
    }

    override fun cancel(id: String) {
        cancelled += id
    }

    override fun pruneFinished() {
        pruneCount++
    }
}

/**
 * A scheduler that installs at once through [packs], on the calling thread, so a test sees the
 * result of a request without waiting for background work.
 */
class ImmediatePackWorkScheduler(private val packs: PackRepository) : PackWorkScheduler {
    override val transfers: Flow<Map<String, PackTransfer>> = MutableStateFlow(emptyMap())

    override fun enqueue(id: String) {
        runBlocking {
            val item = packs.catalogue().first { it.id == id }
            packs.install(item.entry) {}
        }
    }

    override fun cancel(id: String) = Unit

    override fun pruneFinished() = Unit
}
