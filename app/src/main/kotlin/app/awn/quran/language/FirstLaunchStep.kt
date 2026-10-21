package app.awn.quran.language

/** What the app shows before the Quran, in order. */
enum class FirstLaunchStep {
    ChooseLanguage,
    AcceptNotice,
    Done,
}

/**
 * The step a user is on. A user who already accepted the notice (every installation from before the
 * language screen existed) is [FirstLaunchStep.Done] whatever the language flag says, so an upgrade
 * never shows the language screen.
 */
fun firstLaunchStep(noticeAccepted: Boolean, languageChosen: Boolean): FirstLaunchStep =
    when {
        noticeAccepted -> FirstLaunchStep.Done
        !languageChosen -> FirstLaunchStep.ChooseLanguage
        else -> FirstLaunchStep.AcceptNotice
    }
