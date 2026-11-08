package app.awn.quran.feature.learn.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.feature.learn.R

/** The unit's options button; its menu holds "Start again". */
@Composable
internal fun UnitOverflowMenu(onStartAgain: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_learn_more),
                contentDescription = stringResource(R.string.unit_options),
                tint = Awn.field.content,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.unit_start_again)) },
                onClick = {
                    expanded = false
                    onStartAgain()
                },
            )
        }
    }
}
