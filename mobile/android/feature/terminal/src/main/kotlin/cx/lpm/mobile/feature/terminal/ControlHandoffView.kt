package cx.lpm.mobile.feature.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.CapsuleButton
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import kotlinx.coroutines.delay

private const val CLAIM_GRACE_MS = 5_000L

/**
 * In place of the terminal while another surface (a desktop window or another
 * phone) owns it. "Take control" moves ownership here; this view goes once the
 * claim lands, and recovers its button if it never does.
 */
@Composable
internal fun ControlHandoffView(
    ownerLabel: String,
    background: Color,
    onTakeControl: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var taking by remember { mutableStateOf(false) }
    LaunchedEffect(taking) {
        if (!taking) return@LaunchedEffect
        delay(CLAIM_GRACE_MS)
        taking = false
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_terminal),
            contentDescription = null,
            tint = LpmTheme.colors.secondaryLabel,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.terminal_active_on, ownerLabel),
            style = LpmTheme.typography.title2.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.terminal_controlled_elsewhere),
            style = LpmTheme.typography.subheadline,
            color = LpmTheme.colors.secondaryLabel,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        CapsuleButton(
            text = stringResource(
                if (taking) R.string.terminal_taking_control else R.string.terminal_take_control
            ),
            prominent = true,
            textStyle = LpmTheme.typography.body,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                taking = true
                onTakeControl()
            },
            enabled = !taking
        )
    }
}
