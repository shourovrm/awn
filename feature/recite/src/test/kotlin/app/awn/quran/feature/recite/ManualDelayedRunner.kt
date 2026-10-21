package app.awn.quran.feature.recite

/** Collects delayed actions so a test fires them by hand instead of waiting for a clock. */
class ManualDelayedRunner : DelayedRunner {

    class Scheduled(val delayMillis: Long, val action: Runnable) {
        var cancelled = false
    }

    val scheduled = mutableListOf<Scheduled>()

    override fun runAfter(delayMillis: Long, action: Runnable): () -> Unit {
        val entry = Scheduled(delayMillis, action)
        scheduled += entry
        return { entry.cancelled = true }
    }

    /** Runs every action that was not cancelled, the way the clock would. */
    fun fireAll() {
        scheduled.filter { !it.cancelled }.forEach { it.action.run() }
    }
}
