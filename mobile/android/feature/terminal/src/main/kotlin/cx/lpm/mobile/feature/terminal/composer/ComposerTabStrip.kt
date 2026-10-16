package cx.lpm.mobile.feature.terminal.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.terminal.R

/**
 * The prompt tabs above the composer, shown once a terminal holds two or more
 * prepared prompts. A chip previews its prompt and switches to it; × closes it.
 */
@Composable
internal fun ComposerTabStrip(
    items: List<TabStripItem>,
    activeIndex: Int,
    onSwitch: (Int) -> Unit,
    onClose: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.size <= 1) return
    Column(modifier) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                TabChip(
                    index = index,
                    item = item,
                    active = index == activeIndex,
                    onTap = { onSwitch(index) },
                    onClose = { onClose(index) },
                    modifier = Modifier.animateItem()
                )
            }
        }
        ComposerDivider(alpha = 0.4f)
    }
}

@Composable
private fun TabChip(
    index: Int,
    item: TabStripItem,
    active: Boolean,
    onTap: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    Row(
        modifier = modifier
            .background(
                if (active) colors.tint.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
                CircleShape
            )
            .border(
                1.dp,
                if (active) colors.tint.copy(alpha = 0.5f) else Color.Transparent,
                CircleShape
            )
            .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${index + 1}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) colors.tint else colors.secondaryLabel
        )
        Row(
            modifier = Modifier.clickable(role = Role.Button, onClick = onTap),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.preview,
                fontSize = 12.sp,
                color = if (active) colors.label else colors.secondaryLabel,
                maxLines = 1
            )
            if (item.attachmentCount > 0) {
                Icon(
                    painterResource(DesignR.drawable.ic_attach_file),
                    contentDescription = null,
                    tint = colors.secondaryLabel,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
        Icon(
            painterResource(DesignR.drawable.ic_close),
            contentDescription = stringResource(R.string.tab_close),
            tint = colors.secondaryLabel,
            modifier = Modifier
                .size(12.dp)
                .clickable(role = Role.Button, onClick = onClose)
        )
    }
}
