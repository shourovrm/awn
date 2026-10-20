package app.awn.quran.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** The four full-screen field colours. Each mode of the app owns one. */
enum class AwnField {
    /** Read. */
    Paper,
    /** Listen. */
    Green,
    /** Recite, and Read in the system dark theme. */
    Night,
    /** Learn. */
    Ochre,
}

/** The user's choice in Settings: always light, always dark, or follow the device. */
enum class ThemePreference {
    Light,
    Dark,
    System,
}

/** Set once at the app root so every screen's [AwnTheme] follows the stored preference. */
val LocalThemePreference = staticCompositionLocalOf { ThemePreference.System }

/** Everything a screen on one field may draw with. */
data class AwnFieldColors(
    val background: Color,
    val content: Color,
    val buttonFill: Color,
    val onButton: Color,
    val confirmedWord: Color,
    val tentativeWord: Color,
    val wrongWord: Color,
    val hiddenWordBlock: Color,
    val isLight: Boolean,
    /** Behind the ayah the user long-pressed. */
    val selectedAyahFill: Color =
        if (isLight) AwnColors.selectedAyahLight else AwnColors.selectedAyahNight,
    /** Behind the ayah being played. */
    val playingAyahFill: Color =
        if (isLight) AwnColors.playingAyahLight else AwnColors.playingAyahNight,
    /** Behind the word being played, on top of [playingAyahFill]. */
    val playingWordFill: Color =
        if (isLight) AwnColors.playingWordLight else AwnColors.playingWordNight,
    /** Light mark behind bookmarked ayahs. */
    val bookmarkedAyahFill: Color =
        if (isLight) AwnColors.bookmarkedAyahLight else AwnColors.bookmarkedAyahNight,
)

fun awnFieldColors(field: AwnField): AwnFieldColors =
    when (field) {
        AwnField.Paper ->
            AwnFieldColors(
                background = AwnColors.paper,
                content = AwnColors.ink,
                buttonFill = AwnColors.green,
                onButton = AwnColors.paper,
                confirmedWord = AwnColors.green,
                tentativeWord = AwnColors.ochre,
                wrongWord = AwnColors.red,
                hiddenWordBlock = AwnColors.paper,
                isLight = true,
            )
        AwnField.Green ->
            AwnFieldColors(
                background = AwnColors.green,
                content = AwnColors.paper,
                buttonFill = AwnColors.ochre,
                onButton = AwnColors.ink,
                confirmedWord = AwnColors.nightConfirmed,
                tentativeWord = AwnColors.nightTentative,
                wrongWord = AwnColors.nightWrong,
                hiddenWordBlock = AwnColors.paper,
                isLight = false,
            )
        AwnField.Night ->
            AwnFieldColors(
                background = AwnColors.ink,
                content = AwnColors.paper,
                buttonFill = AwnColors.ochre,
                onButton = AwnColors.ink,
                confirmedWord = AwnColors.nightConfirmed,
                tentativeWord = AwnColors.nightTentative,
                wrongWord = AwnColors.nightWrong,
                hiddenWordBlock = AwnColors.paper,
                isLight = false,
            )
        AwnField.Ochre ->
            AwnFieldColors(
                background = AwnColors.ochre,
                content = AwnColors.ink,
                buttonFill = AwnColors.green,
                onButton = AwnColors.paper,
                confirmedWord = AwnColors.green,
                tentativeWord = AwnColors.ink,
                wrongWord = AwnColors.red,
                hiddenWordBlock = AwnColors.paper,
                isLight = true,
            )
    }

private val LocalFieldColors = staticCompositionLocalOf { awnFieldColors(AwnField.Paper) }

/** Access point for the colours of the field the current screen sits on. */
object Awn {
    val field: AwnFieldColors
        @Composable @ReadOnlyComposable get() = LocalFieldColors.current
}

/**
 * Paints the whole screen in the field colour and keeps content out of the system bars. Dark mode
 * turns the Read field into the night field (ink ground, paper text, night word states); the
 * Listen, Recite and Learn fields keep their own colours.
 */
@Composable
fun AwnTheme(field: AwnField = AwnField.Paper, content: @Composable () -> Unit) {
    val useDark =
        when (LocalThemePreference.current) {
            ThemePreference.Light -> false
            ThemePreference.Dark -> true
            ThemePreference.System -> isSystemInDarkTheme()
        }
    val effectiveField = if (field == AwnField.Paper && useDark) AwnField.Night else field
    val colors = awnFieldColors(effectiveField)
    val materialColors =
        if (colors.isLight) {
            lightColorScheme(
                primary = colors.buttonFill,
                onPrimary = colors.onButton,
                background = colors.background,
                onBackground = colors.content,
                surface = colors.background,
                onSurface = colors.content,
            )
        } else {
            darkColorScheme(
                primary = colors.buttonFill,
                onPrimary = colors.onButton,
                background = colors.background,
                onBackground = colors.content,
                surface = colors.background,
                onSurface = colors.content,
            )
        }

    CompositionLocalProvider(LocalFieldColors provides colors) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = AwnTypography.toMaterialTypography(),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = colors.background,
                contentColor = colors.content,
            ) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    content()
                }
            }
        }
    }
}
