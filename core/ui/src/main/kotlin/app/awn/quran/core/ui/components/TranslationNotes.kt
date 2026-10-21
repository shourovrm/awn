package app.awn.quran.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.R

/**
 * Short notes shown in parentheses after the name of a translation whose text needs one. They are
 * kept here, by pack id, because the pack manifest's checksum is pinned and cannot carry them. The
 * Quantum Foundation Bangla text gives the referred meaning of the ayahs, not a literal
 * translation.
 */
private val NOTE_BY_PACK_ID: Map<String, Int> =
    mapOf("bn_quantum" to R.string.translation_note_bn_quantum)

/** The string resource of the note for the translation [packId], or null when it has none. */
@StringRes fun translationNoteFor(packId: String): Int? = NOTE_BY_PACK_ID[packId]

/** [name] followed by the note of the translation [packId] in parentheses, or [name] alone. */
@Composable
fun translationNameWithNote(name: String, packId: String): String {
    val note = translationNoteFor(packId) ?: return name
    return stringResource(R.string.translation_name_with_note, name, stringResource(note))
}
