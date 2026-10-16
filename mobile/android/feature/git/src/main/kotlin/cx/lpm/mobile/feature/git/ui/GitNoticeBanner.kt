package cx.lpm.mobile.feature.git.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.data.git.GitNotice
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * A finished Git op's short confirmation ("Committed successfully", "Pushed") at the
 * top of the screen, until the store lets it go.
 */
@Composable
internal fun GitNoticeBanner(notice: GitNotice?, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = notice,
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
        transitionSpec = {
            (slideInVertically(tween(NOTICE_MS)) { -it } + fadeIn(tween(NOTICE_MS))) togetherWith
                (slideOutVertically(tween(NOTICE_MS)) { -it } + fadeOut(tween(NOTICE_MS)))
        },
        label = "git-notice"
    ) { shown ->
        if (shown == null) return@AnimatedContent
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check_circle_fill),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = shown.text,
                style = LpmTheme.typography.footnote,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

private const val NOTICE_MS = 200
