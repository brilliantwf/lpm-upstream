package cx.lpm.mobile.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Gives the ViewModels of a sheet (or anything else presented over a screen) a store
 * of their own, cleared when it closes — so each presentation starts fresh, like a
 * SwiftUI sheet's `@State`. ViewModels are still built by the host's factory (Hilt).
 */
@Composable
fun PresentationScope(content: @Composable () -> Unit) {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) {
        "PresentationScope needs a ViewModelStoreOwner"
    }
    val owner = remember(parent) { PresentationOwner(parent) }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

internal class PresentationOwner(private val parent: ViewModelStoreOwner) :
    ViewModelStoreOwner,
    HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()

    private val defaults get() = parent as? HasDefaultViewModelProviderFactory

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = defaults?.defaultViewModelProviderFactory ?: ViewModelProvider.NewInstanceFactory()

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras(
            defaults?.defaultViewModelCreationExtras ?: CreationExtras.Empty
        )
            .apply { set(VIEW_MODEL_STORE_OWNER_KEY, this@PresentationOwner) }
}
