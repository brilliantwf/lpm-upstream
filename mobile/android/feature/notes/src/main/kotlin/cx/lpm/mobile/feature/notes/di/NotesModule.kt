package cx.lpm.mobile.feature.notes.di

import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.feature.notes.composer.AndroidNoteMedia
import cx.lpm.mobile.feature.notes.composer.NoteMedia
import cx.lpm.mobile.feature.notes.data.NotesStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object NotesModule {
    @Provides
    @IntoSet
    fun notesStoreFactory(clock: Clock): SessionStore.Factory =
        SessionStore.Factory { session -> NotesStore(session, clock) }
}

@Module
@InstallIn(SingletonComponent::class)
interface NotesBindings {
    @Binds
    fun noteMedia(impl: AndroidNoteMedia): NoteMedia
}
