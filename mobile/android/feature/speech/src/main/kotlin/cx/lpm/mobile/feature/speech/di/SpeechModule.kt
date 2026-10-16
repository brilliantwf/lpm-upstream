package cx.lpm.mobile.feature.speech.di

import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.feature.speech.data.SpeechLink
import cx.lpm.mobile.feature.speech.data.SpeechStore
import cx.lpm.mobile.feature.speech.engine.AndroidAudioSession
import cx.lpm.mobile.feature.speech.engine.AndroidSpeechEngine
import cx.lpm.mobile.feature.speech.engine.AudioSession
import cx.lpm.mobile.feature.speech.engine.ClipPlayer
import cx.lpm.mobile.feature.speech.engine.MediaPlayerClip
import cx.lpm.mobile.feature.speech.engine.SpeechEngine
import cx.lpm.mobile.feature.speech.playback.NowPlaying
import cx.lpm.mobile.feature.speech.playback.ServiceNowPlaying
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Provider

@Module
@InstallIn(SingletonComponent::class)
object SpeechModule {
    @Provides
    @IntoSet
    fun speechLinkFactory(speech: Provider<SpeechStore>): SessionStore.Factory =
        SessionStore.Factory { session -> SpeechLink(session, speech) }
}

@Module
@InstallIn(SingletonComponent::class)
interface SpeechBindings {
    @Binds
    fun speechEngine(impl: AndroidSpeechEngine): SpeechEngine

    @Binds
    fun clipPlayers(impl: MediaPlayerClip.Factory): ClipPlayer.Factory

    @Binds
    fun audioSession(impl: AndroidAudioSession): AudioSession

    @Binds
    fun nowPlaying(impl: ServiceNowPlaying): NowPlaying
}
