package cx.lpm.mobile.feature.speech.text

import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.os.Build

internal object Emoji {
    /**
     * Swift's `isEmoji && isEmojiPresentation`. ICU only exposes the emoji properties
     * from API 28; below it the symbol categories already cover these code points.
     */
    fun isPresentation(cp: Int): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        UCharacter.hasBinaryProperty(cp, UProperty.EMOJI_PRESENTATION)
}
