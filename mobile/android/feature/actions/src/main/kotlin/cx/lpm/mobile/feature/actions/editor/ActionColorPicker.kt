package cx.lpm.mobile.feature.actions.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ToggleRow
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.actions.R as ActionsR
import cx.lpm.mobile.feature.actions.button.ActionTint

/**
 * The desktop's action color picker (iOS `ActionColorPicker`): no color, then the
 * named accents as swatches, each with a deeper shade. A custom color from the config
 * file shows as its own swatch so saving keeps it.
 */
@Composable
internal fun ActionColorPicker(color: String, onChange: (String) -> Unit) {
    val deep = color.endsWith(DEEP)
    val base = if (deep) color.dropLast(DEEP.length) else color
    val custom = color.takeUnless { it.isEmpty() || base in ActionTint.Names }
    val isDark = LpmTheme.colors.isDark
    val gray = LpmTheme.colors.gray
    val swatches = buildList {
        add(
            SwatchSpec(
                stringResource(ActionsR.string.actions_color_none),
                "",
                color.isEmpty(),
                null
            )
        )
        if (custom != null) {
            val fill = ActionTint.of(custom)?.accent(isDark) ?: gray
            add(
                SwatchSpec(stringResource(ActionsR.string.actions_color_custom), custom, true, fill)
            )
        }
        ActionTint.Names.forEach { name ->
            val value = if (deep) "$name$DEEP" else name
            val label = if (deep) {
                stringResource(ActionsR.string.actions_color_deep, name)
            } else {
                name.replaceFirstChar { it.uppercase() }
            }
            add(
                SwatchSpec(label, value, base == name, ActionTint.of(value)?.accent(isDark) ?: gray)
            )
        }
    }
    ListRow {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            swatches.chunked(COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { Swatch(it, { onChange(it.value) }, Modifier.weight(1f)) }
                    repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    ToggleRow(
        title = stringResource(ActionsR.string.actions_color_deeper),
        checked = deep,
        onCheckedChange = { onChange(if (it) "$base$DEEP" else base) },
        enabled = color.isNotEmpty() && custom == null
    )
}

/** One swatch; a null [fill] is "No color". */
private data class SwatchSpec(
    val label: String,
    val value: String,
    val selected: Boolean,
    val fill: Color?
)

@Composable
private fun Swatch(spec: SwatchSpec, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                contentDescription = spec.label
                selected = spec.selected
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .border(
                    2.dp,
                    if (spec.selected) LpmTheme.colors.label else Color.Transparent,
                    CircleShape
                )
                .padding(3.dp)
                .size(30.dp),
            contentAlignment = Alignment.Center
        ) {
            if (spec.fill == null) {
                Icon(
                    painter = painterResource(R.drawable.ic_block),
                    contentDescription = null,
                    tint = LpmTheme.colors.secondaryLabel,
                    modifier = Modifier.size(30.dp)
                )
            } else {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(spec.fill, CircleShape)
                )
            }
        }
    }
}

private const val DEEP = "-deep"
private const val COLUMNS = 7
