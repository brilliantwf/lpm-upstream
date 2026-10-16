package cx.lpm.mobile.feature.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** Copy / Worktree / SSH — what kind of project a row belongs to. */
@Composable
fun ActivityTags(project: ActivityIdentity, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        project.tags.forEach { tag ->
            // A long project name truncates before a tag does: half a word in a
            // capsule says less than the name it stole the room from.
            Text(
                text = tag,
                style = LpmTheme.typography.caption2.copy(fontWeight = FontWeight.SemiBold),
                color = colors.secondaryLabel,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .background(colors.tertiarySystemFill, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/** One project's header while the list is grouped. */
@Composable
fun ActivitySectionHeader(project: ActivityIdentity, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = project.label,
            style = LpmTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = LpmTheme.colors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        ActivityTags(project)
    }
}

@Composable
fun ActivityServicesHeader(groups: List<ActivityServiceGroup>, modifier: Modifier = Modifier) {
    val running = groups.sumOf { it.running.size }
    val total = running + groups.sumOf { it.declared.size }
    val style = LpmTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 1.dp)
    ) {
        Text(
            text = stringResource(R.string.activity_services),
            style = style,
            color = LpmTheme.colors.secondaryLabel,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = pluralStringResource(R.plurals.activity_services_running, total, running, total),
            style = style.copy(fontFeatureSettings = "tnum"),
            color = LpmTheme.colors.secondaryLabel
        )
    }
}

/**
 * What the list leaves out, said where there is room to say it: the projects with
 * nothing running, and the scope of what Activity can see at all — said on the
 * screen, because "why isn't my agent here?" is the question the list can't answer.
 */
@Composable
fun ActivityFooter(quietProjectCount: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (quietProjectCount > 0) {
            FooterLine(
                pluralStringResource(
                    R.plurals.activity_quiet_projects,
                    quietProjectCount,
                    quietProjectCount
                )
            )
        }
        FooterLine(stringResource(R.string.activity_scope_note))
    }
}

@Composable
private fun FooterLine(text: String) {
    Text(
        text = text,
        style = LpmTheme.typography.caption1,
        color = LpmTheme.colors.tertiaryLabel,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Shown while a hold keeps rows from moving and some belong higher up. */
@Composable
fun ActivitySortNowButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = LpmTheme.colors.secondaryLabel
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_swap_vert),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.activity_sort_now),
            style = LpmTheme.typography.caption1.copy(fontWeight = FontWeight.Medium),
            color = color
        )
    }
}
