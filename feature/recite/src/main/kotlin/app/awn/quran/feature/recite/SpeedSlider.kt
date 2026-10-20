package app.awn.quran.feature.recite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.awn.quran.core.ui.theme.Awn
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTypography

private const val TRACK_ALPHA = 0.3f
private val trackShape = RoundedCornerShape(AwnSpacing.switchKnobCorner)
private val thumbShape = RoundedCornerShape(AwnSpacing.sliderThumbCorner)

/**
 * A continuous slider from Slower (0) to Faster (1): an 8 dp track in the field ink at 30 %, a 24
 * dp thumb in the accent with 7 dp corners, and the two end labels underneath.
 */
@OptIn(ExperimentalMaterial3Api::class) // Custom thumb and track slots are still experimental.
@Composable
fun SpeedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = Awn.field
    val speedLabel = stringResource(R.string.recite_speed)
    Column(modifier) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = speedLabel },
            thumb = {
                Box(
                    Modifier.size(AwnSpacing.sliderThumbSize)
                        .clip(thumbShape)
                        .background(field.buttonFill),
                )
            },
            track = { sliderState ->
                Box(
                    Modifier.fillMaxWidth()
                        .height(AwnSpacing.sliderTrackHeight)
                        .clip(trackShape)
                        .background(field.content.copy(alpha = TRACK_ALPHA)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(sliderState.value)
                            .fillMaxHeight()
                            .background(field.buttonFill),
                    )
                }
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.recite_slower), style = AwnTypography.small)
            Text(stringResource(R.string.recite_faster), style = AwnTypography.small)
        }
    }
}
