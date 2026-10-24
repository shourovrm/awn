package app.awn.quran.feature.duas.state

import androidx.annotation.StringRes
import app.awn.quran.feature.duas.R
import app.awn.quran.feature.duas.content.Dua
import app.awn.quran.feature.duas.content.DuaOpening
import app.awn.quran.feature.duas.data.DuaProgress

/** The chips on the Duas home; each one narrows every list and count on the screen. */
enum class DuasFilter(@StringRes val labelResource: Int) {
    All(R.string.duas_filter_all),
    Rabbana(R.string.duas_filter_rabbana),
    Rabbi(R.string.duas_filter_rabbi),
    Prophets(R.string.duas_filter_prophets),
    Saved(R.string.duas_filter_saved),
    Learned(R.string.duas_filter_learned),
    ;

    fun accepts(dua: Dua, progress: DuaProgress): Boolean =
        when (this) {
            All -> true
            Rabbana -> dua.opening == DuaOpening.Rabbana
            Rabbi -> dua.opening == DuaOpening.Rabbi
            Prophets -> dua.prophetKey != null
            Saved -> progress.isSaved(dua.id)
            Learned -> progress.isLearned(dua.id)
        }
}
