package cx.lpm.mobile.core.tailnet

import android.content.Context
import cx.lpm.mobile.core.data.client.TailnetRouter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/** Where the node keeps its keys: never backed up, since they identify this phone. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TailnetDirectory

/** The one thread the node's blocking calls run on, in order. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TailnetCommands

@Module
@InstallIn(SingletonComponent::class)
interface TailnetModule {
    @Binds
    fun builtInTailscale(impl: DefaultBuiltInTailscale): BuiltInTailscale

    /** Fills `:core:data`'s optional router, so connections route through the node. */
    @Binds
    fun tailnetRouter(tailscale: BuiltInTailscale): TailnetRouter

    @Binds
    fun node(impl: GoTailnetNode): TailnetNode

    @Binds
    fun network(impl: ConnectivityNetworkReporter): NetworkReporter

    @Binds
    fun visibility(impl: ProcessAppVisibility): AppVisibility
}

@Module
@InstallIn(SingletonComponent::class)
object TailnetProviders {
    @Provides
    @TailnetDirectory
    fun directory(@ApplicationContext context: Context): File =
        File(context.noBackupFilesDir, "tailnet")

    @Provides
    @Singleton
    @TailnetCommands
    fun commands(): CoroutineDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lpm-tailnet").apply { isDaemon = true }
    }.asCoroutineDispatcher()
}
