package app.awn.quran.feature.read.page

import android.util.Log
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
    /**
     * The rows this page was placed with: the printed lines, or new lines when the chosen size made
     * a printed line too wide. The Hafs fallback has its own.
     */
    val rows: List<PageRow>,
    val families: PageFamilies,
    /** The height of the whole page; more than the screen body when the page scrolls. */
    val pageHeightPx: Float,
)

/**
 * Measures the tokens twice: first at a large size to find the font size that fits the page, then
 * at that size, so the placed rectangles are the widths the text really draws with. Loads the
 * typeface of the page font as a side effect, which is why this runs off the main thread.
 *
 * [size] says how large the page is drawn (see [planPage]); the rows of the result may be broken
 * differently from [rows] when the size is too big for the printed lines.
 */
internal fun measurePage(
    rows: List<PageRow>,
    families: PageFamilies,
    density: Density,
    measurer: TextMeasurer,
    size: PagePlacement,
): MeasuredPage {
    val coarseStyle = pageTextStyle(families.text, MEASURE_FONT_PX, density)
    val coarse = measureTokens(rows, coarseStyle, MEASURE_FONT_PX, measurer)
    val coarseFontSize = planPage(rows, size, coarse::widthEm).geometry.fontSizePx
    val style = pageTextStyle(families.text, coarseFontSize, density)
    val measurements = measureTokens(rows, style, coarseFontSize, measurer)
    val plan = planPage(rows, size, measurements::widthEm)
    val hasBasmala = rows.any { it is PageRow.BasmalaRow }
    val basmalaWidthEm =
        if (hasBasmala) {
            val probe = pageTextStyle(families.plain, MEASURE_FONT_PX, density)
            measurer.measure(BASMALA_TEXT, probe).size.width / MEASURE_FONT_PX
        } else {
            null
        }
    check(isUsable(plan.geometry)) {
        "The placement of the page is not finite: ${plan.geometry.fontSizePx}"
    }
    return MeasuredPage(
        geometry = plan.geometry,
        measurements = measurements,
        basmalaWidthEm = basmalaWidthEm,
        rows = plan.rows,
        families = families,
        pageHeightPx = plan.pageHeightPx,
    )
}

/** True when the font size is positive and every token sits at a finite place. */
private fun isUsable(geometry: PageGeometry): Boolean =
    geometry.fontSizePx.isFinite() &&
        geometry.fontSizePx > 0f &&
        geometry.textLines.all { line ->
            line.top.isFinite() &&
                line.tokens.all { placement ->
                    placement.left.isFinite() && placement.right.isFinite()
                }
        }

/** How long the measuring of one page may take before the page is drawn in the fallback font. */
internal const val MEASURE_TIMEOUT_MS = 10_000L

/**
 * Measures [layout] with its own print; if that throws, produces a non-finite placement or takes
 * longer than [MEASURE_TIMEOUT_MS], logs the page and print and measures the Unicode text in the
 * plain QPC Hafs font instead, so the reader always sees text. [measureOwnPrint] and
 * [measureFallback] are the two measurements, apart so a test can make the first one fail.
 */
// Any failure of the text engine must leave text on the page, so the catch is meant to be wide.
@Suppress("TooGenericExceptionCaught")
internal suspend fun measureWithFallback(
    layout: PageLayout,
    measureOwnPrint: () -> MeasuredPage,
    measureFallback: () -> MeasuredPage,
    timeoutMs: Long = MEASURE_TIMEOUT_MS,
): MeasuredPage {
    val failure: Throwable? =
        try {
            return coroutineScope {
                val own = async(PageWork.dispatcher) { measureOwnPrint() }
                withTimeout(timeoutMs) { own.await() }
            }
        } catch (error: TimeoutCancellationException) {
            error
        } catch (error: CancellationException) {
            // The page left the screen; this is not a failure of the page.
            throw error
        } catch (error: Exception) {
            error
        }
    Log.e(TAG, "Page ${layout.page} in ${layout.script} cannot be measured; using Hafs", failure)
    return withContext(PageWork.dispatcher) { measureFallback() }
}

private const val TAG = "PageMeasurement"

/** The fonts a page is drawn with: [text] for words, [plain] for the banner and Basmalah. */
internal data class PageFamilies(val text: FontFamily?, val plain: FontFamily?)

/**
 * The pixel size a page is placed in: [widthPx] and the height of the screen body it fits between
 * the page header and number ([bodyHeightPx]), how large it is drawn compared with the default
 * ([sizeFactor], 1 is the printed page), and whether it is the centred block of pages 1 and 2.
 */
internal data class PagePlacement(
    val widthPx: Float,
    val bodyHeightPx: Float,
    val sizeFactor: Float,
    val isCentredBlock: Boolean,
    /** The slots the page height is divided into: 15, or more for some IndoPak pages. */
    val slotCount: Int = PageGeometry.SLOTS,
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
 * rotation) is drawn at once. Bounded to the page on screen and two on each side, because a page
 * holds its placed tokens and the font families it was measured with.
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
        const val DEFAULT_CAPACITY = 5
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
                // Each measurement has its own measurer: its cache is not shared safely, and a
                // timed-out measurement may still be running when the fallback starts.
                fun newMeasurer() =
                    TextMeasurer(
                        fontFamilyResolver,
                        density,
                        LayoutDirection.Ltr,
                        MEASURER_CACHE_SIZE,
                    )
                value =
                    measureWithFallback(
                            layout,
                            measureOwnPrint = {
                                measurePage(rows, families, density, newMeasurer(), size)
                            },
                            measureFallback = {
                                measurePage(
                                    buildPageRows(layout, hasGlyphFont = false),
                                    PageFamilies(families.plain, families.plain),
                                    density,
                                    newMeasurer(),
                                    size,
                                )
                            },
                        )
                        .also { sharedPageMeasurements[key] = it }
            }
        }
    return cached ?: measured
}

private const val MEASURER_CACHE_SIZE = 4
