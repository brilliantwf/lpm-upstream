package cx.lpm.mobile.feature.git.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.theme.LpmColors
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.GitFile
import cx.lpm.mobile.feature.git.R

/**
 * A changed file's status the way the desktop's file tree shows it: a letter in its
 * color (A/U added, D deleted, R renamed, M modified, ! conflicted), plus the jump
 * menu's icon and a spoken name.
 */
internal enum class GitFileStatus(val letter: String, val icon: Int, val spoken: Int) {
    Added("A", DesignR.drawable.ic_add_circle, R.string.git_status_added),
    Untracked("U", DesignR.drawable.ic_help, R.string.git_status_untracked),
    Deleted("D", DesignR.drawable.ic_do_not_disturb_on, R.string.git_status_deleted),
    Renamed("R", DesignR.drawable.ic_arrow_circle_right, R.string.git_status_renamed),
    Conflicted("!", DesignR.drawable.ic_warning, R.string.git_status_conflicted),
    Modified("M", DesignR.drawable.ic_edit_circle, R.string.git_status_modified);

    fun color(colors: LpmColors): Color = when (this) {
        Added, Untracked -> colors.green
        Deleted, Conflicted -> colors.red
        Renamed -> colors.cyan
        Modified -> colors.blue
    }

    companion object {
        fun of(status: String): GitFileStatus = when (status) {
            "added" -> Added
            "untracked" -> Untracked
            "deleted" -> Deleted
            "renamed" -> Renamed
            "conflicted" -> Conflicted
            else -> Modified
        }
    }
}

/** The file's name: what follows the last slash. */
internal val GitFile.name: String get() = lastPathComponent(path)

/** The folder the file is in, empty at the repository's root. */
internal val GitFile.directory: String
    get() = path.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = "")

/** The status letter on a tinted rounded square, in front of a file's name. */
@Composable
internal fun FileStatusBadge(status: String, modifier: Modifier = Modifier) {
    val s = GitFileStatus.of(status)
    val color = s.color(LpmTheme.colors)
    val spoken = stringResource(s.spoken)
    Box(
        modifier = modifier
            .size(18.dp)
            .background(color.copy(alpha = BADGE_ALPHA), RoundedCornerShape(5.dp))
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = s.letter,
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

/** A file's name over its folder, the name carrying the weight. */
@Composable
internal fun FileName(file: GitFile, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = file.name,
            style = LpmTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = LpmTheme.colors.label,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis
        )
        if (file.directory.isNotEmpty()) {
            Text(
                text = file.directory,
                style = LpmTheme.typography.caption1,
                color = LpmTheme.colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis
            )
        }
    }
}

private const val BADGE_ALPHA = 0.15f
