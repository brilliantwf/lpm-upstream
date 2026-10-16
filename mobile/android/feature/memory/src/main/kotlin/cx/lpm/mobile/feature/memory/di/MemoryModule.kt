package cx.lpm.mobile.feature.memory.di

import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.feature.memory.data.MemoryStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object MemoryModule {
    @Provides
    @IntoSet
    fun memoryStoreFactory(clock: Clock): SessionStore.Factory =
        SessionStore.Factory { session -> MemoryStore(session, clock) }
}
