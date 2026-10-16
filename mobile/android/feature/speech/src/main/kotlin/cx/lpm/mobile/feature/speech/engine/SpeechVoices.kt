package cx.lpm.mobile.feature.speech.engine

import java.util.Locale

/** What the engine says about one of its voices, before it's offered to the user. */
data class EngineVoice(
    val name: String,
    val locale: Locale,
    /** `Voice.QUALITY_*`: 100 very low … 500 very high. */
    val quality: Int,
    val needsNetwork: Boolean,
    val notInstalled: Boolean
)

/**
 * The voices worth offering (iOS `SpeechPrefs.installedVoices`): those for the
 * phone's language that are downloaded and speak offline — a voice that needs the
 * network would send the text away, which the device engine promises not to do —
 * best quality first, the engine's own default before its peers, then the phone's
 * region, then by name. A language's alias ("en-US-language", the engine's pick for
 * it) would repeat one of the voices, so it goes too.
 */
object SpeechVoices {
    fun installed(
        voices: Collection<EngineVoice>,
        locale: Locale = Locale.getDefault(),
        engineDefault: String? = null
    ): List<SpeechVoice> = voices
        .filter { it.locale.language == locale.language && !it.needsNetwork && !it.notInstalled }
        .filterNot { it.name.endsWith(LANGUAGE_ALIAS) }
        .sortedWith(
            compareByDescending<EngineVoice> { it.quality }
                .thenByDescending { it.name == engineDefault }
                .thenByDescending { it.locale.toLanguageTag() == locale.toLanguageTag() }
                .thenBy { it.name }
        )
        .map { voice ->
            SpeechVoice(
                id = voice.name,
                name = label(voice, locale),
                quality = quality(voice.quality),
                languageTag = voice.locale.toLanguageTag(),
                isEngineDefault = voice.name == engineDefault
            )
        }

    /**
     * Engines name voices for machines ("en-us-x-sfg-local"), so a row reads as its
     * language and region plus the voice's own short code.
     */
    fun label(voice: EngineVoice, displayLocale: Locale = Locale.getDefault()): String {
        val code = GOOGLE_NAME.matchEntire(voice.name)?.groupValues?.get(1) ?: return voice.name
        return "${voice.locale.getDisplayName(displayLocale)} · ${code.uppercase(Locale.ROOT)}"
    }

    private fun quality(value: Int): SpeechVoice.Quality = when {
        value >= QUALITY_VERY_HIGH -> SpeechVoice.Quality.Premium
        value >= QUALITY_HIGH -> SpeechVoice.Quality.Enhanced
        else -> SpeechVoice.Quality.Default
    }

    private val GOOGLE_NAME =
        Regex("[a-z]{2,3}-[a-z0-9]{2,3}-x-([a-z0-9]+)-(?:local|network|embedded)")
    private const val LANGUAGE_ALIAS = "-language"
    private const val QUALITY_HIGH = 400
    private const val QUALITY_VERY_HIGH = 500
}
