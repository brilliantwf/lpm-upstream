package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** A title, the current [value] in gray and a − / + stepper ("Font size  12"). */
@Composable
fun StepperRow(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier
) {
    ListRow(
        modifier = modifier,
        verticalPadding = 10.dp,
        trailing = {
            Text(
                text = value.toString(),
                style = LpmTheme.typography.body.copy(fontFeatureSettings = "tnum"),
                color = LpmTheme.colors.secondaryLabel
            )
            Spacer(Modifier.width(10.dp))
            LpmStepper(value = value, onValueChange = onValueChange, range = range, label = title)
        }
    ) {
        Text(
            text = title,
            style = LpmTheme.typography.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
