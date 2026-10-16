package cx.lpm.mobile.feature.automations.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.PlainTextField
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.trimmingWhitespace
import cx.lpm.mobile.feature.automations.FollowupComposer
import cx.lpm.mobile.feature.automations.R
import cx.lpm.mobile.feature.automations.editor.PlainKeyboard

/**
 * A plain reply field in the composer's pill — what a run's conversation offers until
 * the full composer (attachments, history, rewrite) fills its slot.
 */
@Composable
fun ReplyBar(composer: FollowupComposer, modifier: Modifier = Modifier) {
    var text by rememberSaveable(composer.composerId) { mutableStateOf("") }
    val canSend = !composer.disabled && text.trimmingWhitespace().isNotEmpty()
    val colors = LpmTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.systemGroupedBackground)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(colors.secondarySystemGroupedBackground, RoundedCornerShape(24.dp))
            .padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            PlainTextField(
                value = text,
                onValueChange = { if (!composer.disabled) text = it },
                placeholder = composer.placeholder,
                keyboardOptions = PlainKeyboard,
                maxLines = MAX_LINES,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Box(
            Modifier
                .padding(start = 4.dp, bottom = 2.dp)
                .size(36.dp)
                .clickableNoIndication(
                    onClick = {
                        composer.onSend(text.trimmingWhitespace())
                        text = ""
                    },
                    enabled = canSend
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(
                        if (canSend) colors.tint else colors.tertiarySystemFill,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_arrow_upward),
                    contentDescription = stringResource(R.string.chat_send),
                    tint = if (canSend) Color.White else colors.secondaryLabel.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private const val MAX_LINES = 5
