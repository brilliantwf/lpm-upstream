package cx.lpm.mobile.feature.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.session.ApprovalPairingState
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.LinkButton
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.PrimaryButton
import cx.lpm.mobile.core.designsystem.component.SecondaryButton
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * The waiting sheet for approve-on-Mac pairing: the user tapped a nearby Mac and now
 * confirms on the Mac itself. Shows the shared match code, waits for Allow, and
 * reports a decline or timeout — always offering to type the pairing code instead.
 */
@Composable
internal fun ApprovalPairingSheet(
    approval: ApprovalPairingState,
    macName: String,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onEnterCode: () -> Unit
) {
    ModalSheet(onDismissRequest = onCancel, fullHeight = false, dismissible = false, plain = true) {
        ApprovalPairingContent(approval, macName, onCancel, onRetry, onEnterCode)
    }
}

@Composable
internal fun ApprovalPairingContent(
    approval: ApprovalPairingState,
    macName: String,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onEnterCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mac = macName.ifEmpty { stringResource(R.string.your_mac) }
    Column(
        modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (approval) {
            is ApprovalPairingState.Waiting -> Waiting(
                approval.matchCode,
                mac,
                onCancel,
                onEnterCode
            )

            is ApprovalPairingState.Denied -> when (approval.reason) {
                "busy" -> Failure(
                    DesignR.drawable.ic_hourglass,
                    stringResource(R.string.approval_busy_title),
                    stringResource(R.string.approval_busy_message, mac),
                    onRetry,
                    onCancel,
                    onEnterCode
                )

                LpmClient.BAD_ADDRESS_REASON -> Failure(
                    DesignR.drawable.ic_warning,
                    stringResource(R.string.approval_bad_address_title),
                    stringResource(R.string.approval_bad_address_message, mac),
                    onRetry,
                    onCancel,
                    onEnterCode
                )

                else -> Failure(
                    DesignR.drawable.ic_do_not_touch,
                    stringResource(R.string.approval_declined_title),
                    approval.message ?: stringResource(R.string.approval_declined_message),
                    onRetry,
                    onCancel,
                    onEnterCode
                )
            }

            ApprovalPairingState.TimedOut -> Failure(
                DesignR.drawable.ic_timer_off,
                stringResource(R.string.approval_timeout_title),
                stringResource(R.string.approval_timeout_message, mac),
                onRetry,
                onCancel,
                onEnterCode
            )

            ApprovalPairingState.Requesting -> Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ActivityIndicator(size = ActivityIndicatorSize.Large)
                Text(
                    stringResource(R.string.approval_contacting, mac),
                    style = LpmTheme.typography.headline,
                    textAlign = TextAlign.Center
                )
                EnterCodeButton(onEnterCode)
            }
        }
    }
}

@Composable
private fun Waiting(code: String, mac: String, onCancel: () -> Unit, onEnterCode: () -> Unit) {
    val colors = LpmTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painterResource(DesignR.drawable.ic_verified_user),
            contentDescription = null,
            tint = colors.blue,
            modifier = Modifier.size(52.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.approval_confirm_title),
                style = LpmTheme.typography.title3.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                stringResource(R.string.approval_confirm_message, mac),
                style = LpmTheme.typography.callout,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = grouped(code),
            style = LpmTheme.typography.largeTitle.copy(
                fontSize = 46.sp,
                lineHeight = 54.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFeatureSettings = "tnum"
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.secondarySystemGroupedBackground, RoundedCornerShape(18.dp))
                .padding(vertical = 12.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActivityIndicator(size = ActivityIndicatorSize.Small)
            Text(
                stringResource(R.string.approval_waiting),
                style = LpmTheme.typography.subheadline,
                color = colors.secondaryLabel
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SecondaryButton(
                stringResource(R.string.cancel),
                onCancel,
                textStyle = LpmTheme.typography.body
            )
            EnterCodeButton(onEnterCode)
        }
    }
}

@Composable
private fun Failure(
    icon: Int,
    title: String,
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onEnterCode: () -> Unit
) {
    val colors = LpmTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = colors.orange,
            modifier = Modifier.size(50.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = LpmTheme.typography.title3.copy(fontWeight = FontWeight.Bold))
            Text(
                message,
                style = LpmTheme.typography.callout,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PrimaryButton(
                stringResource(R.string.try_again_title),
                onRetry,
                textStyle = LpmTheme.typography.body,
                modifier = Modifier.heightIn(min = 60.dp)
            )
            SecondaryButton(
                stringResource(R.string.cancel),
                onCancel,
                textStyle = LpmTheme.typography.body
            )
            EnterCodeButton(onEnterCode)
        }
    }
}

@Composable
private fun EnterCodeButton(onClick: () -> Unit) {
    LinkButton(
        stringResource(R.string.approval_enter_code),
        onClick,
        modifier = Modifier.padding(top = 2.dp)
    )
}

/** A 4-digit match code as "12 34" for legibility; any other length verbatim. */
internal fun grouped(code: String): String = if (code.length ==
    4
) {
    code.take(2) + " " + code.drop(2)
} else {
    code
}
