package app.awn.quran.tabs

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.awn.quran.R

/** The three places the bottom bar leads to. Each keeps its own scroll position and screen. */
enum class AwnTab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Quran(R.string.tab_quran, R.drawable.ic_app_menu_book),
    Learn(R.string.tab_learn, R.drawable.ic_app_school),
    Hifz(R.string.tab_hifz, R.drawable.ic_app_psychology),
}
