package app.awn.quran.onboarding

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.awn.quran.R
import app.awn.quran.core.ui.R as UiR
import app.awn.quran.core.ui.components.AwnButton
import app.awn.quran.core.ui.theme.AwnShapes
import app.awn.quran.core.ui.theme.AwnSpacing
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.core.ui.theme.AwnTypography
import app.awn.quran.core.ui.theme.LocalColourMode
import app.awn.quran.core.ui.theme.WelcomeColours
import app.awn.quran.core.ui.theme.welcomeColours
import app.awn.quran.language.AppLanguage

/**
 * The first screen of a new installation: two large language blocks. The screen's own text is in
 * [selected] straight away, before Continue, so the user sees what the choice does; Continue calls
 * [onContinue], which saves the choice and applies it to the whole app.
 */
@Composable
fun LanguageScreen(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onContinue: () -> Unit,
) {
    val colours = welcomeColours(LocalColourMode.current)
    AwnTheme(colors = colours.field) {
        InLanguage(selected) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = AwnSpacing.screenMargin,
                            end = AwnSpacing.screenMargin,
                            top = AwnSpacing.welcomeTopPadding,
                            bottom = AwnSpacing.screenMargin,
                        ),
            ) {
                AppMark()
                Text(
                    text = stringResource(R.string.language_title),
                    style = headingStyle(selected),
                    color = colours.field.content,
                    modifier = Modifier.padding(top = AwnSpacing.medium),
                )
                LanguageBlocks(selected, onSelect, colours)
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.language_hint),
                    style = AwnTypography.small,
                    color = colours.mutedText,
                    modifier = Modifier.padding(vertical = AwnSpacing.medium),
                )
                AwnButton(
                    label = stringResource(R.string.language_continue),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = colours.field.buttonFill,
                    contentColor = colours.field.onButton,
                )
            }
        }
    }
}

/**
 * Resolves string resources in [language] for [content] without touching the app's locale, which
 * would recreate the activity. Compose reads strings from the resources and configuration locals,
 * so both are replaced.
 */
@Composable
private fun InLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val configuration =
        remember(baseConfiguration, language) {
            Configuration(baseConfiguration).apply { setLocale(language.locale) }
        }
    val localizedResources =
        remember(baseContext, configuration) {
            baseContext.createConfigurationContext(configuration).resources
        }
    CompositionLocalProvider(
        LocalConfiguration provides configuration,
        LocalResources provides localizedResources,
        content = content,
    )
}

/** The lead size without the tight tracking of Latin headings, which breaks Bangla conjuncts. */
private fun headingStyle(language: AppLanguage) =
    AwnTypography.lead.copy(
        letterSpacing = TextUnit.Unspecified,
        lineHeight = if (language == AppLanguage.Bangla) BANGLA_LINE_HEIGHT else LATIN_LINE_HEIGHT,
    )

private val MARK_RING_WIDTH = 2.dp
private val BANGLA_LINE_HEIGHT = 1.35.em
private val LATIN_LINE_HEIGHT = 1.2.em

@Composable
private fun AppMark() {
    Image(
        painter = painterResource(R.drawable.awn_mark),
        contentDescription = stringResource(R.string.app_name),
        modifier = Modifier.size(AwnSpacing.welcomeLogoSize).clip(AwnShapes.control),
    )
}

@Composable
private fun LanguageBlocks(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    colours: WelcomeColours,
) {
    Column(
        modifier = Modifier.padding(top = AwnSpacing.large).fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(AwnSpacing.small),
    ) {
        AppLanguage.entries.forEach { language ->
            LanguageBlock(
                language = language,
                isSelected = language == selected,
                onClick = { onSelect(language) },
                colours = colours,
            )
        }
    }
}

@Composable
private fun LanguageBlock(
    language: AppLanguage,
    isSelected: Boolean,
    onClick: () -> Unit,
    colours: WelcomeColours,
) {
    val fill = if (isSelected) colours.selectedBlockFill else colours.blockFill
    val nameColour = if (isSelected) colours.selectedBlockText else colours.field.content
    val subColour = if (isSelected) colours.selectedBlockSubText else colours.blockSubText
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AwnSpacing.languageBlockHeight)
                .clip(AwnShapes.control)
                .background(fill)
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = AwnSpacing.medium, vertical = AwnSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(language.ownNameRes),
                style = headingStyle(language),
                color = nameColour,
            )
            Text(
                text = stringResource(language.translatedNameRes),
                style = AwnTypography.small,
                color = subColour,
            )
        }
        SelectionMark(isSelected, colours)
    }
}

@Composable
private fun SelectionMark(isSelected: Boolean, colours: WelcomeColours) {
    val markModifier = Modifier.size(AwnSpacing.languageBlockMarkSize).clip(CircleShape)
    if (isSelected) {
        Box(
            markModifier.background(colours.selectedMarkFill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_ui_check),
                contentDescription = null,
                tint = colours.selectedMarkTick,
            )
        }
    } else {
        // An empty ring: the unchosen block shows where the mark would sit.
        Box(markModifier.border(MARK_RING_WIDTH, colours.markRing, CircleShape))
    }
}
