package cx.lpm.mobile.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * Actions the Mac left out because of a config problem, each with what's wrong, so a
 * missing button doesn't go unexplained.
 */
@Composable
fun ActionErrorsNotice(errors: List<String>, modifier: Modifier = Modifier) {
    val color = LpmTheme.colors.orange
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_warning_fill),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = stringResource(
                    if (errors.size == 1) {
                        R.string.ui_action_error_title
                    } else {
                        R.string.ui_action_errors_title
                    }
                ),
                style = LpmTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = color
            )
        }
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                errors.forEach { Text(it, style = LpmTheme.typography.caption1, color = color) }
            }
        }
    }
}
