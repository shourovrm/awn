package app.awn.quran.feature.read.page

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.awn.quran.core.data.ScriptId
import app.awn.quran.feature.read.R

/** The page of the Mushaf that every preview image shows. */
internal const val PREVIEW_PAGE = 50

/** What the Mushaf chooser shows about one edition besides its installed state. */
internal data class PageTypeText(
    @StringRes val name: Int,
    @StringRes val year: Int,
    /** The picture of page 50, or null for an edition without one. */
    @DrawableRes val preview: Int,
)

/** The name of the edition [script] as the app shows it, for summaries outside this module. */
@StringRes fun pageTypeNameRes(script: ScriptId): Int = pageTypeText(script).name

internal fun pageTypeText(script: ScriptId): PageTypeText =
    when (script) {
        ScriptId.V1 ->
            PageTypeText(
                R.string.mushaf_type_v1,
                R.string.mushaf_year_v1,
                R.drawable.mushaf_preview_v1,
            )
        ScriptId.V4Tajweed ->
            PageTypeText(
                R.string.mushaf_type_v4,
                R.string.mushaf_year_v4,
                R.drawable.mushaf_preview_v4,
            )
        ScriptId.IndoPak15 ->
            PageTypeText(
                R.string.mushaf_type_indopak,
                R.string.mushaf_year_indopak,
                R.drawable.mushaf_preview_indopak,
            )
        else ->
            PageTypeText(
                R.string.mushaf_type_v2,
                R.string.mushaf_year_v2,
                R.drawable.mushaf_preview_v2,
            )
    }
