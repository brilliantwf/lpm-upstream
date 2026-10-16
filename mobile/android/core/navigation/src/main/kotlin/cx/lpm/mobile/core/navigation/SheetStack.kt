package cx.lpm.mobile.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * The pages pushed inside a sheet — SwiftUI's `NavigationStack` in a `.sheet`, which
 * the app's back stack doesn't hold. Each page's ViewModels live until it's popped.
 */
@Stable
class SheetNavigator<T : Any> internal constructor(root: T) {
    internal val pages = mutableStateListOf(root)
    private val owners = mutableMapOf<Int, PresentationOwner>()

    val current: T get() = pages.last()

    val canGoBack: Boolean get() = pages.size > 1

    fun push(page: T) {
        pages += page
    }

    fun back() {
        if (pages.size <= 1) return
        owners.remove(pages.lastIndex)?.viewModelStore?.clear()
        pages.removeAt(pages.lastIndex)
    }

    internal fun owner(depth: Int, parent: ViewModelStoreOwner): ViewModelStoreOwner =
        owners.getOrPut(depth) { PresentationOwner(parent) }

    internal fun clear() {
        owners.values.forEach { it.viewModelStore.clear() }
        owners.clear()
    }
}

@Composable
fun <T : Any> rememberSheetNavigator(root: T): SheetNavigator<T> = remember { SheetNavigator(root) }

/** Shows [navigator]'s top page; a push slides in from the trailing edge, system back pops. */
@Composable
fun <T : Any> SheetStack(
    navigator: SheetNavigator<T>,
    modifier: Modifier = Modifier,
    page: @Composable (T) -> Unit
) {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) {
        "SheetStack needs a ViewModelStoreOwner"
    }
    val saveable = rememberSaveableStateHolder()
    DisposableEffect(navigator) { onDispose { navigator.clear() } }
    BackHandler(enabled = navigator.canGoBack) { navigator.back() }
    AnimatedContent(
        targetState = navigator.pages.size,
        modifier = modifier,
        transitionSpec = {
            val pushing = targetState > initialState
            slideInHorizontally(tween(DURATION_MS)) { if (pushing) it else -it / 3 } togetherWith
                slideOutHorizontally(tween(DURATION_MS)) { if (pushing) -it / 3 else it }
        },
        label = "sheet-stack"
    ) { depth ->
        val shown = navigator.pages.getOrNull(depth - 1) ?: return@AnimatedContent
        saveable.SaveableStateProvider(depth) {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides navigator.owner(depth - 1, parent)
            ) {
                page(shown)
            }
        }
    }
}

private const val DURATION_MS = 350
