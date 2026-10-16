package cx.lpm.mobile.feature.projects.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.component.DisclosureChevron
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.ProjectAgentRow
import cx.lpm.mobile.core.ui.AgentToneText
import cx.lpm.mobile.feature.projects.agents.AgentStatusTrail

/**
 * Every terminal a project has going, as one recessed card nested under the
 * project's own row and inset to start beneath its name — so a terminal reads as
 * part of the project above it rather than as another project beside it.
 */
@Composable
fun ProjectTerminalDeck(
    rows: List<ProjectAgentRow>,
    onOpen: (ProjectAgentRow) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .clip(shape)
            .background(LpmTheme.colors.tertiarySystemGroupedBackground)
            .border(0.5.dp, LpmTheme.colors.separator.copy(alpha = 0.5f), shape)
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                Box(
                    Modifier
                        .padding(start = 10.dp)
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(LpmTheme.colors.separator)
                )
            }
            ProjectTerminalRow(
                row = row,
                onOpen = { onOpen(row) },
                enabled = enabled && row.terminalId != null
            )
        }
    }
}

/**
 * One terminal inside a project's deck: the tab's name carries the agent's state,
 * and the trailing edge holds the state's mark and how long it has been so.
 */
@Composable
fun ProjectTerminalRow(
    row: ProjectAgentRow,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val description = listOfNotNull(
        row.title,
        row.state.label,
        row.elapsed(System.currentTimeMillis()),
        row.provider.takeIf { it != row.title }
    ).joinToString(", ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onOpen
            )
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .background(if (pressed) LpmTheme.colors.gray4 else Color.Transparent)
            .heightIn(min = 44.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AgentToneText(
                text = row.title,
                state = row.state,
                style = LpmTheme.typography.subheadline,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (row.shared > 0) {
                Text(
                    text = "+${row.shared}",
                    style = LpmTheme.typography.caption2.copy(fontFeatureSettings = "tnum"),
                    color = LpmTheme.colors.secondaryLabel
                )
            }
        }
        AgentStatusTrail(row, style = LpmTheme.typography.footnote)
        DisclosureChevron()
    }
}
