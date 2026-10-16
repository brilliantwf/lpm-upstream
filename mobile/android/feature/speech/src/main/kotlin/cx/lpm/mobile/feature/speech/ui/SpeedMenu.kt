package cx.lpm.mobile.feature.speech.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.data.prefs.SpeechPrefs
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ContextMenu
import cx.lpm.mobile.core.designsystem.component.ContextMenuItem
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.speech.R

/** The speed capsule, opening the speeds with a check beside the current one. */
@Composable
fun SpeedMenu(rate: Double, onRateChange: (Double) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.speech_speed)
    Box(modifier) {
        Box(
            Modifier
                .size(width = 44.dp, height = 30.dp)
                .background(LpmTheme.colors.label.copy(alpha = 0.07f), CircleShape)
                .clickableNoIndication(onClick = { open = true })
                .clearAndSetSemantics {
                    contentDescription = label
                    stateDescription = rateLabel(rate)
                    role = Role.Button
                    onClick {
                        open = true
                        true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rateLabel(rate),
                style = LpmTheme.typography.footnote.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                // A Menu's label takes the tint on iOS.
                color = LpmTheme.colors.tint,
                maxLines = 1
            )
        }
        ContextMenu(expanded = open, onDismissRequest = { open = false }) {
            SpeechPrefs.RATES.forEach { option ->
                ContextMenuItem(
                    title = rateLabel(option),
                    onClick = { onRateChange(option) },
                    icon = if (option == rate) painterResource(DesignR.drawable.ic_check) else null
                )
            }
        }
    }
}
