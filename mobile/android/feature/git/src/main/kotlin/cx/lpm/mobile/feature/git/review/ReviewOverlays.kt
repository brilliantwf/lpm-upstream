package cx.lpm.mobile.feature.git.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.CapsuleButton
import cx.lpm.mobile.core.designsystem.component.EmptyState
import cx.lpm.mobile.core.designsystem.component.ListCard
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.Shimmer
import cx.lpm.mobile.core.designsystem.component.SkeletonBar
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.git.R

/** The loading ghost, "Can't reach your Mac" with Retry, or "Not a git repository". */
@Composable
internal fun ReviewOverlayContent(
    overlay: ReviewOverlay,
    loadError: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (overlay) {
        ReviewOverlay.Skeleton -> ReviewSkeleton(modifier)

        ReviewOverlay.Unreachable -> Centered(modifier) {
            EmptyState(
                title = stringResource(R.string.git_review_unreachable),
                icon = painterResource(DesignR.drawable.ic_wifi_off),
                description = { Description(loadError.orEmpty()) },
                actions = {
                    CapsuleButton(
                        text = stringResource(R.string.git_retry),
                        onClick = onRetry,
                        prominent = true,
                        textStyle = LpmTheme.typography.body
                    )
                }
            )
        }

        ReviewOverlay.NotRepo -> Centered(modifier) {
            EmptyState(
                title = stringResource(R.string.git_review_not_repo),
                icon = painterResource(DesignR.drawable.ic_folder_off),
                description = { Description(stringResource(R.string.git_review_not_repo_message)) }
            )
        }
    }
}

@Composable
private fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Description(text: String) {
    Text(
        text = text,
        style = LpmTheme.typography.body,
        color = LpmTheme.colors.secondaryLabel,
        textAlign = TextAlign.Center
    )
}

/** The branch row and four file rows, shimmering, until the first snapshot. */
@Composable
private fun ReviewSkeleton(modifier: Modifier = Modifier) {
    val fill = LpmTheme.colors.tertiarySystemFill
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LpmTheme.colors.systemGroupedBackground)
            .padding(horizontal = ListDefaults.Margin)
            .padding(top = ListDefaults.ContentTopPadding),
        verticalArrangement = Arrangement.spacedBy(35.dp)
    ) {
        ListCard {
            Shimmer {
                ListRow {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(34.dp).background(fill, RoundedCornerShape(10.dp)))
                        SkeletonBar(width = 120.dp)
                    }
                }
            }
        }
        ListCard(Modifier.fillMaxWidth()) {
            listOf(160, 120, 190, 140).forEach { width ->
                Shimmer {
                    ListRow {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(20.dp).background(fill, CircleShape))
                            SkeletonBar(width = width.dp)
                        }
                    }
                }
            }
        }
    }
}
