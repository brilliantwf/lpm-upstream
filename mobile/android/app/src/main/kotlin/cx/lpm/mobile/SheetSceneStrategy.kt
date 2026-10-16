package cx.lpm.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.get
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.ModalSheetState
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.navigation.MediumDetent
import cx.lpm.mobile.core.navigation.PlainSheet
import cx.lpm.mobile.core.navigation.SheetDismissal
import cx.lpm.mobile.core.navigation.SheetDismissalScope
import cx.lpm.mobile.core.navigation.SheetPresentation

/**
 * Draws a destination marked with [SheetPresentation] in a sheet over the screens
 * under it, the way iOS presents `.sheet`s; system back or a swipe down pops it.
 */
class SheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val last = entries.lastOrNull() ?: return null
        if (last.metadata[SheetPresentation] != true) return null
        @Suppress("UNCHECKED_CAST")
        return SheetScene(
            key = last.contentKey as T,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            entry = last,
            medium = last.metadata[MediumDetent] == true,
            plain = last.metadata[PlainSheet] == true,
            onBack = onBack
        )
    }
}

private data class SheetScene<T : Any>(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val medium: Boolean,
    private val plain: Boolean,
    private val onBack: () -> Unit
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    private var sheet: ModalSheetState? = null

    override val content: @Composable () -> Unit = {
        val state = rememberModalSheetState(medium).also { sheet = it }
        val dismissal = remember { SheetDismissal() }
        ModalSheet(
            onDismissRequest = onBack,
            state = state,
            dismissible = !dismissal.disabled,
            plain = plain
        ) {
            SheetDismissalScope(dismissal) { entry.Content() }
        }
    }

    // Popped by a button inside it: slide down rather than vanish.
    override suspend fun onRemove() {
        sheet?.hide()
    }
}
