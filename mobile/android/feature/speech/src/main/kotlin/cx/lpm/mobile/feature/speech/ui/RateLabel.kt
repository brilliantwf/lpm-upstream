package cx.lpm.mobile.feature.speech.ui

/** "1x", "1.5x", "0.75x" (iOS `SpeechPrefs.rateLabel`). */
fun rateLabel(rate: Double): String = if (rate % 1.0 == 0.0) "${rate.toInt()}x" else "${rate}x"
