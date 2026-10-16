package cx.lpm.mobile.feature.config.dirs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.DisclosureChevron
import cx.lpm.mobile.core.designsystem.component.EmptyState
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.designsystem.component.ListLeadingSlot
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.PrimaryButton
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.config.R
import cx.lpm.mobile.feature.config.ui.BarBottomGap

/**
 * Picks a folder on the Mac: [title] says what it's for, the bar over the list shows
 * where the browser is, and "Use This Folder" hands that path to [onPick].
 */
@Composable
fun DirBrowserPage(
    title: String,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DirBrowserViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DirBrowserScreen(
        title = title,
        state = state,
        onOpen = viewModel::open,
        onOpenChild = viewModel::openChild,
        onPick = onPick,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
fun DirBrowserScreen(
    title: String,
    state: DirBrowserUiState,
    onOpen: (String) -> Unit,
    onOpenChild: (String) -> Unit,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .background(LpmTheme.colors.systemGroupedBackground)
    ) {
        Column(Modifier.background(barColor())) {
            LpmTopBar(
                title = title,
                modifier = Modifier.padding(top = 16.dp),
                windowInsets = WindowInsets(0),
                leading = {
                    GlassIconButton(
                        icon = painterResource(DesignR.drawable.ic_arrow_back_ios_new),
                        contentDescription = stringResource(R.string.back),
                        onClick = onBack
                    )
                }
            )
            Spacer(Modifier.height(BarBottomGap))
            PathBar(state.listing?.path)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = ListTopPadding, bottom = 24.dp)
            ) {
                DirList(state, onOpen, onOpenChild)
            }
            state.error?.let { error ->
                EmptyState(
                    title = stringResource(R.string.config_dirs_error),
                    icon = painterResource(DesignR.drawable.ic_folder_limited),
                    modifier = Modifier.align(Alignment.Center),
                    description = {
                        Text(
                            text = error,
                            style = LpmTheme.typography.body,
                            color = LpmTheme.colors.secondaryLabel,
                            textAlign = TextAlign.Center
                        )
                    }
                )
            }
        }
        UseFolderBar(enabled = state.listing != null) {
            state.listing?.path?.let(onPick)
        }
    }
}

@Composable
private fun DirList(
    state: DirBrowserUiState,
    onOpen: (String) -> Unit,
    onOpenChild: (String) -> Unit
) {
    val listing = state.listing
    if (listing == null) {
        if (state.loading) {
            ListSection {
                ListRow {
                    ActivityIndicator(size = ActivityIndicatorSize.Small)
                    Text(
                        text = stringResource(R.string.config_loading),
                        style = LpmTheme.typography.body,
                        color = LpmTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
        return
    }
    val tint = LpmTheme.colors.tint
    ListSection {
        listing.parent?.let { parent ->
            ListRow(
                onClick = { onOpen(parent) },
                leading = { RowIcon(DesignR.drawable.ic_arrow_upward, 24.dp) }
            ) {
                Text(stringResource(R.string.config_dirs_up), style = body, color = tint)
            }
        }
        if (listing.dirs.isEmpty()) {
            ListRow {
                Text(
                    text = stringResource(R.string.config_dirs_empty),
                    style = body,
                    color = LpmTheme.colors.secondaryLabel
                )
            }
        }
        listing.dirs.forEach { dir ->
            ListRow(
                onClick = { onOpenChild(dir) },
                leading = { RowIcon(DesignR.drawable.ic_folder, 22.dp) },
                leadingSlot = FolderSlot,
                trailing = { DisclosureChevron(tint = tint.copy(alpha = TERTIARY_ALPHA)) }
            ) {
                Text(dir, style = body, color = tint, modifier = Modifier.weight(1f))
            }
        }
    }
}

private val body @Composable get() = LpmTheme.typography.body

/** A Button row's symbol, in the tint like its title. */
@Composable
private fun RowIcon(icon: Int, size: Dp) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = LpmTheme.colors.tint,
        modifier = Modifier.size(size)
    )
}

/** A `List` under a top `safeAreaInset` starts this far below it. */
private val ListTopPadding = 36.dp

/** A folder row's `HStack(spacing: 12)`: the folder symbol, then the name. */
private val FolderSlot = ListLeadingSlot(width = 22.dp, spacing = 12.dp)

/** iOS's `.bar` material over a grouped sheet. */
@Composable
private fun barColor(): Color = with(LpmTheme.colors) {
    if (isDark) systemGroupedBackground else Color(BAR_LIGHT)
}

private const val TERTIARY_ALPHA = 0.25f
private const val BAR_LIGHT = 0xFFFAFAFD

/** Where the browser is, truncated from the front so the folder's own name shows. */
@Composable
private fun PathBar(path: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_folder_fill),
            contentDescription = null,
            tint = LpmTheme.colors.secondaryLabel,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = path ?: stringResource(R.string.config_dirs_no_path),
            style = LpmTheme.typography.footnote,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun UseFolderBar(enabled: Boolean, onUse: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(barColor())
    ) {
        HorizontalDivider(thickness = Dp.Hairline, color = LpmTheme.colors.separator)
        PrimaryButton(
            text = stringResource(R.string.config_dirs_use),
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onUse()
            },
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
            textStyle = LpmTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
                .heightIn(min = 63.dp)
        )
    }
}
