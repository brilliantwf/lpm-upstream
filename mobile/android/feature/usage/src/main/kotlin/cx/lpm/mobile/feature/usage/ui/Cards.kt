package cx.lpm.mobile.feature.usage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.CapsuleButton
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.usage.R as UsageR

/** The rounded grouped-background card the Stats and Usage screens are built from. */
@Composable
internal fun Modifier.groupedCard(
    radius: Dp = 20.dp,
    color: Color = LpmTheme.colors.secondarySystemGroupedBackground
): Modifier = background(color, RoundedCornerShape(radius))

/** A screen that couldn't load: a warning, what failed, why, and "Try Again". */
@Composable
internal fun ErrorCard(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .groupedCard()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = colors.orange,
            modifier = Modifier.size(37.dp)
        )
        Text(title, style = LpmTheme.typography.headline)
        Text(
            message,
            style = LpmTheme.typography.subheadline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        CapsuleButton(
            text = stringResource(UsageR.string.try_again),
            onClick = onRetry,
            prominent = true,
            textStyle = LpmTheme.typography.headline
        )
    }
}
