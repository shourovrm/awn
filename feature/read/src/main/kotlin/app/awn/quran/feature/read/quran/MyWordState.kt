package app.awn.quran.feature.read.quran

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.feature.read.R

/** Whether the word on the card is in the user's own word list. */
enum class MyWordState {
    NotAdded,
    Added,
}

/**
 * The icon at the start of the word card's heading: a bookmark with a plus while the word is not in
 * the list (a tap adds it), a filled bookmark once it is (nothing to tap).
 */
@Composable
internal fun AddToMyWordsButton(state: MyWordState, onAdd: () -> Unit) {
    val isAdded = state == MyWordState.Added
    val icon = if (isAdded) R.drawable.ic_read_bookmark_added else R.drawable.ic_read_bookmark_add
    val description =
        stringResource(
            if (isAdded) R.string.word_added_to_my_words else R.string.word_add_to_my_words,
        )
    val tapModifier =
        if (isAdded) Modifier else Modifier.clickable(role = Role.Button, onClick = onAdd)
    Box(
        Modifier.requiredSize(AwnSpacing.touchTarget)
            .semantics { contentDescription = description }
            .then(tapModifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Awn.field.onBar)
    }
}
