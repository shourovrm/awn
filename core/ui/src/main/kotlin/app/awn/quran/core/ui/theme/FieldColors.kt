package app.awn.quran.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

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
    /** The top and bottom bars that float over a page. */
    val barBackground: Color,
    val onBar: Color,
    /** Fills drawn behind the text of an ayah or word; the text keeps its own colour. */
    val selectedAyahFill: Color,
    val playingAyahFill: Color,
    val playingWordFill: Color,
    val bookmarkedAyahFill: Color,
    val isLight: Boolean,
)

// Alphas are tuned so that a selected ayah, a playing ayah and a playing word are three distinct
// steps on every page colour, and the text stays above 4.5:1 contrast under all of them.
private const val SELECTED_ALPHA = 0.30f
private const val PLAYING_AYAH_ALPHA = 0.20f
private const val PLAYING_WORD_ALPHA = 0.45f
private const val BOOKMARKED_ALPHA = 0.16f

/** Awn mode, paper field: today's palette. The other Awn fields are variations of it. */
private fun awnPaper() =
    AwnFieldColors(
        background = AwnColors.paper,
        content = AwnColors.ink,
        buttonFill = AwnColors.green,
        onButton = AwnColors.paper,
        confirmedWord = AwnColors.green,
        tentativeWord = AwnColors.ochre,
        wrongWord = AwnColors.red,
        hiddenWordBlock = AwnColors.paper,
        barBackground = AwnColors.green,
        onBar = AwnColors.paper,
        selectedAyahFill = AwnColors.ochre.copy(alpha = SELECTED_ALPHA),
        playingAyahFill = AwnColors.green.copy(alpha = PLAYING_AYAH_ALPHA),
        playingWordFill = AwnColors.ochre.copy(alpha = PLAYING_WORD_ALPHA),
        bookmarkedAyahFill = AwnColors.ochre.copy(alpha = BOOKMARKED_ALPHA),
        isLight = true,
    )

/** Awn mode, a field whose background is dark: light inks and ochre fills. */
private fun awnDarkField(background: Color) =
    awnPaper()
        .copy(
            background = background,
            content = AwnColors.paper,
            buttonFill = AwnColors.ochre,
            onButton = AwnColors.ink,
            confirmedWord = AwnColors.nightConfirmed,
            tentativeWord = AwnColors.nightTentative,
            wrongWord = AwnColors.nightWrong,
            selectedAyahFill = AwnColors.ochre.copy(alpha = SELECTED_ALPHA),
            playingAyahFill = AwnColors.nightConfirmed.copy(alpha = PLAYING_AYAH_ALPHA),
            isLight = false,
        )

private fun awnModeColours(field: AwnField): AwnFieldColors =
    when (field) {
        AwnField.Paper -> awnPaper()
        AwnField.Green -> awnDarkField(AwnColors.green)
        AwnField.Night -> awnDarkField(AwnColors.ink)
        AwnField.Ochre ->
            awnPaper().copy(background = AwnColors.ochre, tentativeWord = AwnColors.ink)
    }

private fun lightPalette() =
    AwnFieldColors(
        background = AwnColors.lightPage,
        content = AwnColors.lightInk,
        buttonFill = AwnColors.green,
        onButton = AwnColors.lightPage,
        confirmedWord = AwnColors.green,
        tentativeWord = AwnColors.lightTentative,
        wrongWord = AwnColors.red,
        hiddenWordBlock = AwnColors.lightHiddenWord,
        barBackground = AwnColors.lightBar,
        onBar = AwnColors.lightInk,
        selectedAyahFill = AwnColors.green.copy(alpha = SELECTED_ALPHA),
        playingAyahFill = AwnColors.ochre.copy(alpha = PLAYING_AYAH_ALPHA + 0.1f),
        playingWordFill = AwnColors.ochre.copy(alpha = PLAYING_WORD_ALPHA),
        bookmarkedAyahFill = AwnColors.ochre.copy(alpha = BOOKMARKED_ALPHA),
        isLight = true,
    )

private fun nightPalette(page: Color, bar: Color, hiddenWord: Color) =
    AwnFieldColors(
        background = page,
        content = AwnColors.nightText,
        buttonFill = AwnColors.nightConfirmed,
        onButton = page,
        confirmedWord = AwnColors.nightConfirmed,
        tentativeWord = AwnColors.nightTentative,
        wrongWord = AwnColors.nightWrong,
        hiddenWordBlock = hiddenWord,
        barBackground = bar,
        onBar = AwnColors.nightText,
        selectedAyahFill = AwnColors.nightConfirmed.copy(alpha = SELECTED_ALPHA),
        playingAyahFill = AwnColors.ochre.copy(alpha = PLAYING_AYAH_ALPHA + 0.1f),
        playingWordFill = AwnColors.ochre.copy(alpha = PLAYING_WORD_ALPHA),
        bookmarkedAyahFill = AwnColors.ochre.copy(alpha = BOOKMARKED_ALPHA),
        isLight = false,
    )

/** The colours of [field] in [mode]; Awn mode is today's palette with one colour per field. */
fun awnFieldColors(field: AwnField, mode: ColourMode = ColourMode.Awn): AwnFieldColors {
    val page =
        when (mode) {
            ColourMode.Awn -> return awnModeColours(field)
            ColourMode.Light -> lightPalette()
            ColourMode.Dark ->
                nightPalette(AwnColors.darkPage, AwnColors.darkBar, AwnColors.darkHiddenWord)
            ColourMode.Black ->
                nightPalette(AwnColors.blackPage, AwnColors.blackBar, AwnColors.blackHiddenWord)
        }
    // Light, Dark and Black are one palette each: every field draws on the page colour, except
    // Green, which stands for a raised surface (a figure tile) and takes the bar colour so it
    // stays visible on the page.
    return if (field == AwnField.Green) {
        page.copy(background = page.barBackground, content = page.onBar)
    } else {
        page
    }
}

private const val MUTED_ALPHA = 0.72f
private const val SOFT_FILL_ALPHA = 0.08f
private const val SEARCH_MATCH_ALPHA = 0.55f
private const val RAISED_LIGHT_MIX = 0.55f
private const val RAISED_DARK_MIX = 0.09f

/** Second lines and hints: the ink of the mode, a little lighter, still above 4.5:1 contrast. */
val AwnFieldColors.mutedContent: Color
    get() = content.copy(alpha = MUTED_ALPHA)

/** A quiet fill for fields that sit on the page, such as the search field. */
val AwnFieldColors.softFill: Color
    get() = content.copy(alpha = SOFT_FILL_ALPHA)

/** The block behind the matched text of a search result: ochre, whatever the field. */
val AwnFieldColors.searchMatchFill: Color
    get() = AwnColors.ochre.copy(alpha = SEARCH_MATCH_ALPHA)

/** The surface of a menu: the page colour lifted toward white (light modes) or the ink (dark). */
val AwnFieldColors.raisedSurface: Color
    get() =
        if (isLight) lerp(background, Color.White, RAISED_LIGHT_MIX)
        else lerp(background, content, RAISED_DARK_MIX)

private const val LIGHT_BAR_LUMINANCE = 0.5f

/**
 * The accent for an icon or mark that sits on the bar: ochre on a dark bar (the green of Awn mode,
 * Dark, Black), the button green on a light bar. Ochre on the green bar is 3.6:1, enough for a 24
 * dp icon and a 4 dp mark but not for text, so labels on the bar keep the full ink.
 */
val AwnFieldColors.accentOnBar: Color
    get() = if (barBackground.luminance() < LIGHT_BAR_LUMINANCE) AwnColors.ochre else buttonFill

private const val SCRIM_LIGHT_ALPHA = 0.5f
private const val SCRIM_DARK_ALPHA = 0.62f

/** The dimming drawn over the page behind a floating card; heavier on dark pages. */
val AwnFieldColors.scrim: Color
    get() = Color.Black.copy(alpha = if (isLight) SCRIM_LIGHT_ALPHA else SCRIM_DARK_ALPHA)
