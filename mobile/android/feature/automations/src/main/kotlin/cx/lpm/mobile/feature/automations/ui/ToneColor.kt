package cx.lpm.mobile.feature.automations.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.automations.format.AutomationTone

/** The iOS system color a tone stands for. */
@Composable
@ReadOnlyComposable
fun AutomationTone.color(): Color = when (this) {
    AutomationTone.Green -> LpmTheme.colors.green
    AutomationTone.Red -> LpmTheme.colors.red
    AutomationTone.Orange -> LpmTheme.colors.orange
    AutomationTone.Blue -> LpmTheme.colors.blue
    AutomationTone.Secondary -> LpmTheme.colors.secondaryLabel
}
