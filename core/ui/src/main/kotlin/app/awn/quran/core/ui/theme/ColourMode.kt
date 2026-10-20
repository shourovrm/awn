package app.awn.quran.core.ui.theme

/** The two palettes offered for daytime reading. */
enum class DayColours {
    /** Paper, green bars and ochre accents: the Madani Mushaf's own colours. */
    Awn,
    /** A white page, neutral grey bars and a green accent. */
    Light,
}

/** The two palettes offered for night reading. */
enum class NightColours {
    /** A #121212 page with light text. */
    Dark,
    /** A #000000 page, which switches the pixels off on OLED screens. */
    Black,
}

/** The four palettes the app can be drawn in. */
enum class ColourMode(val isNight: Boolean) {
    Awn(isNight = false),
    Light(isNight = false),
    Dark(isNight = true),
    Black(isNight = true),
}

/**
 * The user's colour choices: which palette is used by day, which by night, and whether day or night
 * follows the device ([followDevice]) or the manual state [manualNight], which the Quran screen's
 * day/night button flips.
 */
data class ColourSettings(
    val day: DayColours = DayColours.Awn,
    val night: NightColours = NightColours.Dark,
    val followDevice: Boolean = true,
    val manualNight: Boolean = false,
) {

    fun isNight(deviceIsDark: Boolean): Boolean = if (followDevice) deviceIsDark else manualNight

    /** The palette to draw with, given whether the device is currently in its dark theme. */
    fun resolve(deviceIsDark: Boolean): ColourMode =
        if (isNight(deviceIsDark)) {
            when (night) {
                NightColours.Dark -> ColourMode.Dark
                NightColours.Black -> ColourMode.Black
            }
        } else {
            when (day) {
                DayColours.Awn -> ColourMode.Awn
                DayColours.Light -> ColourMode.Light
            }
        }

    /**
     * The settings after the day/night button is pressed. The button takes over from the device: it
     * sets [followDevice] to false and flips whatever is on screen now, so one press always changes
     * what the user sees.
     */
    fun toggledNight(deviceIsDark: Boolean): ColourSettings =
        copy(followDevice = false, manualNight = !isNight(deviceIsDark))
}
