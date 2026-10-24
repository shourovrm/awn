package app.awn.quran

/** What the first recite shows, in order: the notice, the one-time download, then the bar. */
internal enum class ReciteEntryStep {
    Notice,
    Download,
    Bar,
}

/** The notice comes first, so nobody downloads or recites before reading it. */
internal fun reciteEntryStep(noticeAccepted: Boolean, isModelInstalled: Boolean): ReciteEntryStep =
    when {
        !noticeAccepted -> ReciteEntryStep.Notice
        !isModelInstalled -> ReciteEntryStep.Download
        else -> ReciteEntryStep.Bar
    }
