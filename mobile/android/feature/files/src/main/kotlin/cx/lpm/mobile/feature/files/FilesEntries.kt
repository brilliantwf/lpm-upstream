package cx.lpm.mobile.feature.files

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.navigation.FilePreviewKey
import cx.lpm.mobile.core.navigation.Navigator
import cx.lpm.mobile.core.navigation.SheetPresentation
import cx.lpm.mobile.feature.files.preview.FilePreviewRoute

/** The file preview sheet; a file a Markdown file links to opens in another one over it. */
fun EntryProviderScope<NavKey>.filesEntries(navigator: Navigator) {
    entry<FilePreviewKey>(metadata = SheetPresentation.plainMetadata) { key ->
        FilePreviewRoute(
            key = key,
            onDismiss = { if (navigator.current == key) navigator.back() },
            onOpenFile = { paths ->
                navigator.navigate(
                    FilePreviewKey(key.project, paths, session = key.session, depth = key.depth + 1)
                )
            }
        )
    }
}
