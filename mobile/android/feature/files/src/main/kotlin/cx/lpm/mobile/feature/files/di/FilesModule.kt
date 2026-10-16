package cx.lpm.mobile.feature.files.di

import android.content.Context
import android.webkit.MimeTypeMap
import cx.lpm.mobile.core.data.di.ApplicationScope
import cx.lpm.mobile.core.data.mac.ThisDevice
import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.feature.files.fetch.FileFetches
import cx.lpm.mobile.feature.files.fetch.ScratchSpace
import cx.lpm.mobile.feature.files.preview.FilePreviewProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** Where the preview reads a fetched file and renders its pages, off the main thread. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PreviewDispatcher

@Module
@InstallIn(SingletonComponent::class)
object FilesModule {
    @Provides
    @Singleton
    fun scratchSpace(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope
    ): ScratchSpace = ScratchSpace(
        root = File(context.cacheDir, FilePreviewProvider.FOLDER),
        disk = Dispatchers.IO.limitedParallelism(1),
        scope = scope
    )

    @Provides
    @IntoSet
    fun fileFetchesFactory(space: ScratchSpace, device: ThisDevice): SessionStore.Factory =
        SessionStore.Factory { session ->
            FileFetches(
                session,
                space,
                device.noun,
                MimeTypeMap.getSingleton()::getMimeTypeFromExtension
            )
        }

    @Provides
    @PreviewDispatcher
    fun previewDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
