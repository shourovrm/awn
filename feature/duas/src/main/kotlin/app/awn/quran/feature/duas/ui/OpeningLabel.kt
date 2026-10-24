package app.awn.quran.feature.duas.ui

import androidx.annotation.StringRes
import app.awn.quran.feature.duas.R
import app.awn.quran.feature.duas.content.DuaOpening

/** The tag a card shows for how the dua opens; null for an opening that needs no tag. */
@StringRes
internal fun openingLabelResource(opening: DuaOpening): Int? =
    when (opening) {
        DuaOpening.Rabbana -> R.string.duas_opening_rabbana
        DuaOpening.Rabbi -> R.string.duas_opening_rabbi
        DuaOpening.Allahumma -> R.string.duas_opening_allahumma
        DuaOpening.Other -> null
    }
