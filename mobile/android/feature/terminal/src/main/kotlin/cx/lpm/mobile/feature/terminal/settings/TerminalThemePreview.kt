package cx.lpm.mobile.feature.terminal.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.web.TerminalTheme
import cx.lpm.mobile.feature.terminal.R

private val TrafficLights = listOf(Color(0xFFFF5E59), Color(0xFFFFBD2E), Color(0xFF3DC94A))

/**
 * A small live swatch of the terminal theme — traffic-light dots over a sample
 * command line on the theme's background — so the picker shows what each theme
 * and font size looks like, as the desktop's settings do.
 */
@Composable
fun TerminalThemePreview(theme: TerminalTheme, fontSize: Int, modifier: Modifier = Modifier) {
    val prompt = stringResource(R.string.terminal_settings_preview_prompt)
    val command = stringResource(R.string.terminal_settings_preview_command)
    val green = LpmTheme.colors.green
    ListRow(modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(theme.backgroundColor, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TrafficLights.forEach { Box(Modifier.size(10.dp).background(it, CircleShape)) }
            }
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = green)) { append(prompt) }
                    withStyle(SpanStyle(color = theme.foregroundColor)) { append(command) }
                },
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize.sp
            )
        }
    }
}
