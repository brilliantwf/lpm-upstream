package cx.lpm.mobile.feature.memory.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import cx.lpm.mobile.feature.memory.data.MemoryFeedback
import cx.lpm.mobile.feature.memory.data.MemoryHaptic

/** Plays the store's haptics that land while this screen shows (not ones from before). */
@Composable
internal fun MemoryHaptics(feedback: MemoryFeedback?) {
    val haptics = LocalHapticFeedback.current
    var played by remember { mutableStateOf(feedback?.seq) }
    LaunchedEffect(feedback) {
        val next = feedback ?: return@LaunchedEffect
        if (next.seq == played) return@LaunchedEffect
        played = next.seq
        haptics.performHapticFeedback(
            when (next.haptic) {
                MemoryHaptic.Success -> HapticFeedbackType.Confirm
                MemoryHaptic.Error -> HapticFeedbackType.Reject
            }
        )
    }
}
