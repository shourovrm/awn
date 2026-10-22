package app.awn.quran.feature.read.quran

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.feature.read.R

/** Whether the word on the card is in the user's own word list. */
enum class MyWordState {
    NotAdded,
    Added,
}

/** "Add to my words" while the word is not in the list, a plain line once it is. */
@Composable
internal fun MyWordLine(state: MyWordState, onAdd: () -> Unit) {
    val field = Awn.field
    when (state) {
        MyWordState.NotAdded ->
            Box(
                Modifier.defaultMinSize(minHeight = AwnSpacing.touchTarget)
                    .clickable(role = Role.Button, onClick = onAdd),
                Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.word_add_to_my_words),
                    style = AwnTypography.bodyStrong,
                    color = field.onBar,
                )
            }
        MyWordState.Added ->
            Text(
                text = stringResource(R.string.word_added_to_my_words),
                style = AwnTypography.body,
                color = field.onBar,
            )
    }
}
