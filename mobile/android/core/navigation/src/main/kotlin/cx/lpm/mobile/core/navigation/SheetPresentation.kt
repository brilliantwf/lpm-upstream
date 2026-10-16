package cx.lpm.mobile.core.navigation

import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.metadata

/**
 * Marks a destination as a sheet over the screen it was opened from (SwiftUI's
 * `.sheet`) rather than a pushed screen: `entry<K>(metadata = SheetPresentation.metadata)`.
 * The app's `NavDisplay` draws such an entry in a sheet; popping it slides the sheet away.
 * [mediumMetadata] opens it at iOS's medium detent, pulled up to the large one;
 * [plainMetadata] gives it the plain sheet background of a sheet with no `Form`.
 */
object SheetPresentation : NavMetadataKey<Boolean> {
    val metadata: Map<String, Any> = metadata { put(SheetPresentation, true) }

    val mediumMetadata: Map<String, Any> = metadata {
        put(SheetPresentation, true)
        put(MediumDetent, true)
    }

    val plainMetadata: Map<String, Any> = metadata {
        put(SheetPresentation, true)
        put(PlainSheet, true)
    }
}

/** A [SheetPresentation] sheet that opens at the medium detent. */
object MediumDetent : NavMetadataKey<Boolean>

/** A [SheetPresentation] sheet on the plain sheet background rather than a grouped list's. */
object PlainSheet : NavMetadataKey<Boolean>
