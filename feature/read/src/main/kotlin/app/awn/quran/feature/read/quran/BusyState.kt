package app.awn.quran.feature.read.quran

/** What a busy bottom bar is doing, which decides the icon and the action of the corner button. */
enum class BusyState {
    AudioPlaying,
    AudioPaused,
    MicrophoneOn,
    MicrophoneOff,
}
