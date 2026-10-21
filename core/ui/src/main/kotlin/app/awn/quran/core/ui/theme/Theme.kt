package app.awn.quran.core.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The four field colours of Awn mode. A screen names the field it sits on; in the Light, Dark and
 * Black modes every field draws on that mode's page colour (see [awnFieldColors]).
 */
enum class AwnField {
    /** The Quran page and the index. */
    Paper,
    /** A raised surface such as a figure tile. */
    Green,
    /** The recitation screen. */
    Night,
    /** Learn. */
    Ochre,
}

/** The palette in use, set once at the app root from the user's [ColourSettings]. */
val LocalColourMode = staticCompositionLocalOf { ColourMode.Awn }

private const val LIGHT_LUMINANCE = 0.5f

/**
 * The colour painted under the system navigation bar when a screen does not paint its own page
 * there (the tab bar does). Null means the page colour is under it.
 */
val LocalNavigationBarBackground = staticCompositionLocalOf<Color?> { null }

private val LocalFieldColors = staticCompositionLocalOf { awnFieldColors(AwnField.Paper) }

/** Access point for the colours of the field the current screen sits on. */
object Awn {
    val field: AwnFieldColors
        @Composable @ReadOnlyComposable get() = LocalFieldColors.current
}

/**
 * Paints the whole screen in the field colour, keeps content out of the system bars and makes the
 * status-bar and navigation-bar icons dark on light colours and light on dark colours.
 */
@Composable
fun AwnTheme(
    field: AwnField = AwnField.Paper,
    barBehindStatusBar: Boolean = false,
    // A screen with its own palette (the language screen) passes the colours directly.
    colors: AwnFieldColors = awnFieldColors(field, LocalColourMode.current),
    content: @Composable () -> Unit,
) {
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
    // A screen that draws its bar behind the status bar (AwnTopBar) leaves the top inset to that
    // bar, so the status-bar icons must contrast with the bar, not with the page.
    val statusBarBackground = if (barBehindStatusBar) colors.barBackground else colors.background
    val navigationBarBackground = LocalNavigationBarBackground.current ?: colors.background
    SystemBarIcons(
        isLightStatusBar = statusBarBackground.luminance() > LIGHT_LUMINANCE,
        isLightNavigationBar = navigationBarBackground.luminance() > LIGHT_LUMINANCE,
    )
    val contentInsets =
        if (barBehindStatusBar) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        } else {
            WindowInsets.safeDrawing
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
                Box(Modifier.fillMaxSize().windowInsetsPadding(contentInsets)) {
                    content()
                }
            }
        }
    }
}

/**
 * Sets the colour of the status-bar and navigation-bar icons from the colour behind them.
 *
 * `enableEdgeToEdge()` picks the icon colour once, from the device's night setting, so a Light app
 * on a dark device kept white icons on cream. The icons must follow the colour the app paints,
 * which only this theme knows, so it sets them whenever the field changes.
 */
@Composable
private fun SystemBarIcons(isLightStatusBar: Boolean, isLightNavigationBar: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = isLightStatusBar
        controller.isAppearanceLightNavigationBars = isLightNavigationBar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The three-button bar otherwise gets a grey scrim that hides the page colour.
            window.isNavigationBarContrastEnforced = false
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
