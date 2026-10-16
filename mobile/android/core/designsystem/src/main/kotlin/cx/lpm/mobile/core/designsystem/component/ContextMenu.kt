package cx.lpm.mobile.core.designsystem.component

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

private val PanelShape = RoundedCornerShape(34.dp)
private val ShadowMargin = 40.dp
private val ScreenEdge = 8.dp
private val Overhang = 6.dp

/**
 * The iOS 26 menu (`Menu`, `.contextMenu`): a frosted rounded panel that grows out
 * of its anchor and covers it, top edges aligned, overhanging the anchor's outer
 * side slightly. Place it next to the anchor in a `Box`, like `DropdownMenu`. Fill
 * it with [ContextMenuItem], [ContextMenuSubmenu] and [ContextMenuDivider]; picking
 * an item closes the menu.
 */
@Composable
fun ContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = expanded
    if (!visible.currentState && !visible.targetState) return

    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    var origin by remember { mutableStateOf(TransformOrigin.Center) }
    val dismiss by rememberUpdatedState(onDismissRequest)
    val positions = remember(density) {
        MenuPositionProvider(
            margin = with(density) { ShadowMargin.roundToPx() },
            edge = with(density) { ScreenEdge.roundToPx() },
            overhang = with(density) { Overhang.roundToPx() },
            onOrigin = { origin = it }
        )
    }
    Popup(
        popupPositionProvider = positions,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true, clippingEnabled = false)
    ) {
        val transition = rememberTransition(visible, label = "menu")
        val scale by transition.animateFloat(
            transitionSpec = { spring(dampingRatio = 0.82f, stiffness = 520f) },
            label = "scale"
        ) { if (it) 1f else 0.4f }
        val alpha by transition.animateFloat(transitionSpec = {
            tween(140)
        }, label = "alpha") { if (it) 1f else 0f }
        val controller = remember { ContextMenuController { dismiss() } }
        Box(
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = origin
                }
                .pointerInput(Unit) { detectTapGestures { dismiss() } }
                .padding(ShadowMargin)
        ) {
            CompositionLocalProvider(LocalContextMenuController provides controller) {
                val submenu = controller.submenu
                // Each menu opens at its top, not where the one it covers was scrolled to.
                val scroll = remember(submenu) { ScrollState(0) }
                MenuPanel(maxHeight = windowHeight * 0.6f, scroll = scroll) {
                    if (submenu == null) {
                        content()
                    } else {
                        SubmenuHeader(submenu = submenu, onBack = controller::back)
                        ContextMenuDivider()
                        submenu.items(this)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuPanel(
    maxHeight: Dp,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LpmTheme.colors
    Column(
        modifier = modifier
            .outerShadow(PanelShape, colors.menuShadow, blur = 32.dp, offsetY = 8.dp)
            .background(colors.menuBackground, PanelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .widthIn(min = 250.dp, max = 320.dp)
            .width(IntrinsicSize.Max)
            .heightIn(max = maxHeight)
            .verticalScroll(scroll)
            .padding(vertical = 10.dp),
        content = content
    )
}

/**
 * Lines the panel's top with the anchor's top and its outer edge [overhang] past the
 * anchor's, on whichever side of the screen the anchor sits, kept [edge] inside the
 * window. Reports the anchor's center so the panel grows out of it.
 */
private class MenuPositionProvider(
    private val margin: Int,
    private val edge: Int,
    private val overhang: Int,
    private val onOrigin: (TransformOrigin) -> Unit
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val panelWidth = popupContentSize.width - margin * 2
        val panelHeight = popupContentSize.height - margin * 2
        val alignEnd = anchorBounds.center.x > windowSize.width / 2
        val left = if (alignEnd) {
            anchorBounds.right + overhang - panelWidth
        } else {
            anchorBounds.left - overhang
        }.coerceIn(edge, (windowSize.width - edge - panelWidth).coerceAtLeast(edge))
        val top = anchorBounds.top.coerceIn(
            edge,
            (windowSize.height - edge - panelHeight).coerceAtLeast(edge)
        )
        val x = left - margin
        val y = top - margin
        if (popupContentSize.width > 0 && popupContentSize.height > 0) {
            onOrigin(
                TransformOrigin(
                    pivotFractionX = (anchorBounds.center.x - x).toFloat() / popupContentSize.width,
                    pivotFractionY = (anchorBounds.center.y - y).toFloat() / popupContentSize.height
                )
            )
        }
        return IntOffset(x, y)
    }
}
