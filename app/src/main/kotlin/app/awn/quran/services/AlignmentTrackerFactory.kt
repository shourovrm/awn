package app.awn.quran.services

import app.awn.quran.tracker.AlignmentTracker
import app.awn.quran.tracker.Tracker
import app.awn.quran.tracker.TrackerConfig
import app.awn.quran.tracker.TrackerFactory

/** Makes the real tracker; the recite feature asks for a new one each time a range is loaded. */
object AlignmentTrackerFactory : TrackerFactory {
    override fun create(config: TrackerConfig): Tracker = AlignmentTracker(config)
}
