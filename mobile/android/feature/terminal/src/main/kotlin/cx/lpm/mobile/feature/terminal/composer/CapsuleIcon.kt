package cx.lpm.mobile.feature.terminal.composer

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** A symbol leading a prominent capsule's white label ("Use this", "Rewrite"). */
@Composable
internal fun CapsuleIcon(icon: Int, modifier: Modifier = Modifier, tint: Color = Color.White) {
    Icon(
        painterResource(icon),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(15.dp)
    )
}
