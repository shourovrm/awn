package app.awn.quran.feature.read.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.ScriptId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val MEASURE_FONT_PX = 200f
/** Used when the text engine reports no baseline, as the test fonts of Robolectric do. */
internal const val FALLBACK_BASELINE_EM = 0.8f
internal const val RING_WIDTH_EM = 1.1f

/** The text style of page text at [fontSizePx]; no extra line height, so baselines are exact. */
@Suppress("DEPRECATION") // includeFontPadding is the only way to get exact font metrics here.
internal fun pageTextStyle(family: FontFamily?, fontSizePx: Float, density: Density): TextStyle =
    TextStyle(
        fontFamily = family,
        fontSize = with(density) { fontSizePx.toSp() },
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle =
            LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Proportional,
                trim = LineHeightStyle.Trim.None,
            ),
    )

/** Widths of the tokens of a page, in ems of its font, and where its first baseline falls. */
internal class TokenMeasurements(
    private val widthsEm: Map<String, Float>,
    val firstBaselineEm: Float,
) {
    fun widthEm(token: LineToken): Float =
        when (token) {
            is LineToken.WordToken -> widthsEm.getValue(token.text)
            is LineToken.RosetteToken ->
                token.glyphCode?.let { widthsEm.getValue(it) } ?: RING_WIDTH_EM
        }
}

internal fun measureTokens(
    rows: List<PageRow>,
    style: TextStyle,
    fontSizePx: Float,
    measurer: TextMeasurer,
): TokenMeasurements {
    val texts =
        rows
            .filterIsInstance<PageRow.TextRow>()
            .flatMap { it.tokens }
            .mapNotNull { token ->
                when (token) {
                    is LineToken.WordToken -> token.text
                    is LineToken.RosetteToken -> token.glyphCode
                }
            }
            .distinct()
    var firstBaseline = 0f
    val widths = HashMap<String, Float>()
    for (text in texts) {
        val result = measurer.measure(text, style, softWrap = false, maxLines = 1)
        widths[text] = result.size.width / fontSizePx
        if (firstBaseline == 0f) firstBaseline = result.firstBaseline / fontSizePx
    }
    val baselineEm = if (firstBaseline > 0f) firstBaseline else FALLBACK_BASELINE_EM
    return TokenMeasurements(widths, baselineEm)
}

internal fun toArabicIndicDigits(number: Int): String =
    number.toString().map { digit -> '٠' + (digit - '0') }.joinToString("")

/** Where the measuring of a page runs; tests set it to run inline so a page is ready at once. */
internal object PageWork {
    @Volatile var dispatcher: CoroutineDispatcher = Dispatchers.Default
}

/** A page placed and the measurements it was placed with. */
internal class MeasuredPage(
    val geometry: PageGeometry,
    val measurements: TokenMeasurements,
    /** Width of the Basmalah line in ems of the plain font, or null when the page has none. */
    val basmalaWidthEm: Float?,
)

/**
 * Measures the tokens twice: first at a large size to find the font size that fits the page, then
 * at that size, so the placed rectangles are the widths the text really draws with. Loads the
 * typeface of the page font as a side effect, which is why this runs off the main thread.
 *
 * [pageHeightPx] is the whole height of the page, [scale] times the height of the screen body.
 */
internal fun measurePage(
    rows: List<PageRow>,
    families: PageFamilies,
    density: Density,
    measurer: TextMeasurer,
    size: PagePlacement,
): MeasuredPage {
    val isCentredBlock = size.isCentredBlock
    val coarseStyle = pageTextStyle(families.text, MEASURE_FONT_PX, density)
    val coarse = measureTokens(rows, coarseStyle, MEASURE_FONT_PX, measurer)
    val coarseSize =
        layoutPage(
                rows,
                isCentredBlock,
                size.widthPx,
                size.pageHeightPx,
                coarse::widthEm,
                size.scale,
            )
            .fontSizePx
    val style = pageTextStyle(families.text, coarseSize, density)
    val measurements = measureTokens(rows, style, coarseSize, measurer)
    val geometry =
        layoutPage(
            rows,
            isCentredBlock,
            size.widthPx,
            size.pageHeightPx,
            measurements::widthEm,
            size.scale,
        )
    val hasBasmala = rows.any { it is PageRow.BasmalaRow }
    val basmalaWidthEm =
        if (hasBasmala) {
            val probe = pageTextStyle(families.plain, MEASURE_FONT_PX, density)
            measurer.measure(BASMALA_TEXT, probe).size.width / MEASURE_FONT_PX
        } else {
            null
        }
    return MeasuredPage(geometry, measurements, basmalaWidthEm)
}

/** The fonts a page is drawn with: [text] for words, [plain] for the banner and Basmalah. */
internal data class PageFamilies(val text: FontFamily?, val plain: FontFamily?)

/** The pixel size a page is placed in, and whether it is the centred block of pages 1 and 2. */
internal data class PagePlacement(
    val widthPx: Float,
    val pageHeightPx: Float,
    val scale: Float,
    val isCentredBlock: Boolean,
    /** True when the page is taller than the screen body and the body scrolls. */
    val isScrolling: Boolean = scale > 1f,
)

/**
 * What identifies a loaded layout without comparing every word. The screen loads a page again when
 * it is recreated (after Settings, a rotation), and that load is a new object with the same
 * content; keying by the object would make the page wait for a new measurement each time.
 */
private data class LayoutFingerprint(
    val page: Int,
    val script: ScriptId,
    val lineCount: Int,
    val itemCount: Int,
) {
    constructor(
        layout: PageLayout,
    ) : this(
        page = layout.page,
        script = layout.script,
        lineCount = layout.lines.size,
        itemCount = layout.lines.sumOf { line -> line.items.size },
    )
}

/** Everything the placement of a page depends on. */
internal class PageMeasureKey(
    layout: PageLayout,
    private val hasGlyphFont: Boolean,
    private val families: PageFamilies,
    private val size: PagePlacement,
    private val density: Density,
) {
    private val fingerprint = LayoutFingerprint(layout)

    override fun equals(other: Any?): Boolean =
        other is PageMeasureKey &&
            other.fingerprint == fingerprint &&
            other.hasGlyphFont == hasGlyphFont &&
            other.families == families &&
            other.size == size &&
            other.density.density == density.density &&
            other.density.fontScale == density.fontScale

    override fun hashCode(): Int =
        listOf(fingerprint, hasGlyphFont, families, size, density.density, density.fontScale)
            .hashCode()
}

/**
 * The most recently used pages that were placed, so a page that comes back (a swipe back, a
 * rotation) is drawn at once. Bounded, because a page holds its placed tokens.
 */
internal class PageMeasurementCache(private val capacity: Int = DEFAULT_CAPACITY) {
    private val entries =
        object : LinkedHashMap<PageMeasureKey, MeasuredPage>(capacity, LOAD_FACTOR, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<PageMeasureKey, MeasuredPage>,
            ): Boolean = size > capacity
        }

    @Synchronized operator fun get(key: PageMeasureKey): MeasuredPage? = entries[key]

    @Synchronized
    operator fun set(key: PageMeasureKey, page: MeasuredPage) {
        entries[key] = page
    }

    @get:Synchronized
    val size: Int
        get() = entries.size

    companion object {
        const val DEFAULT_CAPACITY = 8
        private const val LOAD_FACTOR = 0.75f
    }
}

internal val sharedPageMeasurements = PageMeasurementCache()

/**
 * The placed [layout] at [size], or null while it is being measured on [PageWork.dispatcher]. A
 * page that was measured before comes back from [sharedPageMeasurements] in the same frame. The
 * main thread never measures text, so turning to a page does not wait for its font to load.
 */
@Composable
internal fun rememberMeasuredPage(
    layout: PageLayout,
    rows: List<PageRow>,
    hasGlyphFont: Boolean,
    families: PageFamilies,
    size: PagePlacement,
): MeasuredPage? {
    val density = LocalDensity.current
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val key = PageMeasureKey(layout, hasGlyphFont, families, size, density)
    val cached = sharedPageMeasurements[key]
    // While a new size is being measured, the state still holds the page measured before it.
    val measured by
        produceState<MeasuredPage?>(initialValue = cached, key) {
            if (cached == null) {
                value =
                    withContext(PageWork.dispatcher) {
                        // Its own measurer: a TextMeasurer keeps a cache that is not shared safely.
                        val measurer =
                            TextMeasurer(
                                fontFamilyResolver,
                                density,
                                LayoutDirection.Ltr,
                                MEASURER_CACHE_SIZE,
                            )
                        measurePage(
                                rows,
                                families,
                                density,
                                measurer,
                                size,
                            )
                            .also { sharedPageMeasurements[key] = it }
                    }
            }
        }
    return cached ?: measured
}

private const val MEASURER_CACHE_SIZE = 4
