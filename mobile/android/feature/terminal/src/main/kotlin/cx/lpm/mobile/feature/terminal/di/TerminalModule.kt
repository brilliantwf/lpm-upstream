package cx.lpm.mobile.feature.terminal.di

import cx.lpm.mobile.core.data.mac.ThisDevice
import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.feature.terminal.composer.AndroidComposerMedia
import cx.lpm.mobile.feature.terminal.composer.ComposerSessions
import cx.lpm.mobile.feature.terminal.history.HistoryStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object TerminalModule {
    @Provides
    @IntoSet
    fun composerSessionsFactory(
        media: AndroidComposerMedia,
        device: ThisDevice
    ): SessionStore.Factory = SessionStore.Factory { session ->
        ComposerSessions(session, media, Dispatchers.IO, device.noun)
    }

    @Provides
    @IntoSet
    fun historyStoreFactory(): SessionStore.Factory = SessionStore.Factory(::HistoryStore)
}
