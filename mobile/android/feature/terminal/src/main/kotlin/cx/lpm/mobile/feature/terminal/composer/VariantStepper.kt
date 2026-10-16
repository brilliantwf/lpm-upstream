package cx.lpm.mobile.feature.terminal.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.terminal.R

internal val VariantRange = 1..5

/** The −N+ variants control (1–5) beside each rewrite. */
@Composable
internal fun VariantStepper(count: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.08f), CircleShape)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton(
            DesignR.drawable.ic_remove,
            stringResource(R.string.rewrite_fewer),
            count > VariantRange.first
        ) {
            onChange(count - 1)
        }
        Text(
            text = "$count",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = LpmTheme.colors.label,
            modifier = Modifier.widthIn(min = 14.dp)
        )
        StepButton(
            DesignR.drawable.ic_add,
            stringResource(R.string.rewrite_more),
            count < VariantRange.last
        ) {
            onChange(count + 1)
        }
    }
}

@Composable
private fun StepButton(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LpmTheme.colors
    Box(
        modifier = Modifier.size(
            18.dp
        ).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(icon),
            contentDescription = label,
            tint = if (enabled) colors.label else colors.secondaryLabel.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
    }
}
