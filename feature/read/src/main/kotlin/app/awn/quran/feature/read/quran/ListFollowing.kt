package app.awn.quran.feature.read.quran

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Scrolls the list to the playing ayah while following, to the ayah being recited, and to a newly
 * selected one, and tells the screen when the reader scrolls by hand. Nothing scrolls before the
 * fonts are ready, because the list has no items until then.
 */
@Composable
internal fun FollowListTargets(
    content: PageContent,
    environment: ListPageEnvironment,
    listState: LazyListState,
    areFontsReady: Boolean,
) {
    LaunchedEffect(content, environment.playingAyah, environment.followRequest, areFontsReady) {
        if (!areFontsReady) return@LaunchedEffect
        val index = environment.playingAyah?.let(content::indexOf) ?: -1
        if (index >= 0 && environment.isFollowing) listState.showItem(index)
    }
    // The reciter moves on below the screen, and a page with translations shows only an ayah or
    // two, so without this the fills of the words being recited are never seen.
    LaunchedEffect(content, environment.recitedAyah, areFontsReady) {
        if (!areFontsReady) return@LaunchedEffect
        val index = environment.recitedAyah?.let(content::indexOf) ?: -1
        if (index >= 0) listState.showItem(index)
    }
    LaunchedEffect(content, environment.selection?.first, environment.isPanelOpen, areFontsReady) {
        if (!areFontsReady) return@LaunchedEffect
        val index = environment.selection?.first?.let(content::indexOf) ?: -1
        if (index >= 0) listState.showItem(index)
    }
    // A reader who scrolls the list by hand stops it following the audio.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) environment.onUserScrolled()
        }
    }
}

/**
 * Scrolls so that item [index] is on screen, but only when it is not already fully visible, and
 * then leaves [TOP_MARGIN_SHARE] of the list height above it. An ayah that is already in view never
 * moves, so the list does not jump under a reader who is reading it.
 */
private suspend fun LazyListState.showItem(index: Int) {
    val info = layoutInfo
    val visibleTop = info.beforeContentPadding
    val visibleBottom = info.viewportSize.height - info.afterContentPadding
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item != null && isFullyVisible(item.offset, item.size, visibleTop, visibleBottom)) return
    animateScrollToItem(index, -topMarginFor(visibleBottom - visibleTop))
}
