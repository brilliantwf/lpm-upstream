package cx.lpm.mobile.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.ElevatedColors
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.designsystem.theme.elevated
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A sheet with iOS's look: 38 dp corners, grouped background (elevated in dark
 * mode), a light scrim. [fullHeight] is iOS's large detent, up to the status bar;
 * otherwise the sheet fits its content. A sheet whose [state] opens at the medium
 * detent shows iOS 26's grabber and, while at that detent, floats 8 dp clear of
 * the screen's sides and bottom; a fixed-height sheet standing in for that detent
 * passes [floating] to look the same. Put a [SheetScaffold] inside. A sheet that isn't
 * [dismissible] (`interactiveDismissDisabled`) can't be swiped or tapped away;
 * system back still asks to dismiss it. Close it from a button with
 * [ModalSheetState.dismiss]. [contentInsets] is what the content keeps clear of;
 * pass none to draw under the navigation bar. A [plain] sheet has the default sheet
 * background rather than a grouped list's, as a sheet with no `Form`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    fullHeight: Boolean = true,
    state: ModalSheetState = rememberModalSheetState(),
    dismissible: Boolean = true,
    contentInsets: WindowInsets = WindowInsets.navigationBars,
    plain: Boolean = false,
    floating: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LpmTheme.colors.elevated()
    val target = state.sheet.targetValue
    val float by animateFloatAsState(
        if (
            (state.medium && target == SheetValue.PartiallyExpanded) ||
            (floating && target != SheetValue.Hidden)
        ) {
            1f
        } else {
            0f
        },
        label = "floating"
    )
    val geometry = rememberSheetGeometry(state.sheet, medium = state.medium)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = FloatingInset * float)
            .graphicsLayer { translationY = -geometry.raise * float },
        sheetState = state.sheet,
        sheetGesturesEnabled = dismissible,
        shape = RectangleShape,
        containerColor = Color.Transparent,
        contentColor = colors.label,
        scrimColor = colors.scrim,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
        properties = ModalBottomSheetProperties(shouldDismissOnClickOutside = dismissible)
    ) {
        ElevatedColors {
            val height = if (fullHeight) Modifier.fillMaxHeight() else Modifier
            Box(
                height
                    .graphicsLayer {
                        shape = SheetShape(float, geometry.visibleHeight(float))
                        clip = true
                    }
                    .background(
                        if (plain) colors.systemBackground else colors.systemGroupedBackground
                    )
            ) {
                Column(height.windowInsetsPadding(contentInsets), content = content)
                if (state.medium || floating) Grabber(Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

/** iOS 26's sheet grabber, over the content rather than above it. */
@Composable
private fun Grabber(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 6.dp)
            .size(width = 57.dp, height = 4.dp)
            .background(LpmTheme.colors.tertiaryLabel, CircleShape)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberSheetGeometry(sheet: SheetState, medium: Boolean): SheetGeometry {
    val window = LocalWindowInfo.current
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.getTop(density)
    val inset = with(density) { FloatingInset.toPx() }
    return remember(sheet, medium, window, statusBar, inset) {
        SheetGeometry(sheet, medium, window, statusBar, inset)
    }
}

/** Where a sheet sits on the screen, in pixels. */
@OptIn(ExperimentalMaterial3Api::class)
private class SheetGeometry(
    private val sheet: SheetState,
    private val medium: Boolean,
    private val window: WindowInfo,
    private val statusBar: Int,
    private val inset: Float
) {
    private val screen get() = window.containerSize.height

    /**
     * How far a floating sheet is lifted: at the medium detent, from Material's (half
     * the height under the status bar) to iOS's (its top 47.5 % of the way down the
     * screen); otherwise just clear of the bottom edge.
     */
    val raise: Float
        get() = if (medium) {
            (screen * MEDIUM_DETENT - (screen - statusBar) / 2f).coerceAtLeast(0f)
        } else {
            inset
        }

    /** How much of the sheet is above the screen's bottom edge while [floating]. */
    fun visibleHeight(floating: Float): Float {
        val offset = runCatching { sheet.requireOffset() }.getOrDefault(0f)
        return screen - statusBar - offset + raise * floating
    }
}

/**
 * The sheet's outline: top corners always, and while [floating] only the part above
 * the screen's bottom edge, less the floating gap, with its bottom corners rounded.
 */
private class SheetShape(private val floating: Float, private val visibleHeight: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val f = floating
        val r = with(density) { Corner.toPx() }
        val bottom = if (f > 0f) {
            (
                visibleHeight - with(density) {
                    FloatingInset.toPx()
                } * f
                ).coerceIn(r * 2, size.height)
        } else {
            size.height
        }
        val bottomRadius = CornerRadius(r * f)
        return Outline.Generic(
            Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = bottom,
                        topLeftCornerRadius = CornerRadius(r),
                        topRightCornerRadius = CornerRadius(r),
                        bottomRightCornerRadius = bottomRadius,
                        bottomLeftCornerRadius = bottomRadius
                    )
                )
            }
        )
    }
}

/** A [ModalSheet]'s position, so a button can slide it away before it's removed. */
@Stable
class ModalSheetState
@OptIn(ExperimentalMaterial3Api::class)
internal constructor(
    internal val sheet: SheetState,
    internal val medium: Boolean,
    private val scope: CoroutineScope
) {
    /** Slide the sheet down, then run [then] — usually what clears its flag. */
    @OptIn(ExperimentalMaterial3Api::class)
    fun dismiss(then: () -> Unit) {
        scope.launch { sheet.hide() }.invokeOnCompletion { then() }
    }

    /** Slide the sheet down, as when its destination is popped off the back stack. */
    @OptIn(ExperimentalMaterial3Api::class)
    suspend fun hide() = sheet.hide()
}

/**
 * A sheet that opens at iOS's [medium] detent — half the screen, pulled up to the
 * large one — when its content is taller; pair it with `fullHeight`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberModalSheetState(medium: Boolean = false): ModalSheetState {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = !medium)
    val scope = rememberCoroutineScope()
    return remember(sheet, medium, scope) { ModalSheetState(sheet, medium, scope) }
}

private val Corner = 38.dp
private val FloatingInset = 8.dp
private const val MEDIUM_DETENT = 0.525f
