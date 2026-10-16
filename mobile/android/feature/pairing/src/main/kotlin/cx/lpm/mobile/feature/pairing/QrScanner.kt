package cx.lpm.mobile.feature.pairing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** Full-screen QR scanner: hands the first pairing code it reads to [onScan] and closes. */
@Composable
internal fun QrScannerSheet(onScan: (PairPayload) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalSheetState()
    ModalSheet(
        onDismissRequest = onDismiss,
        state = sheet,
        contentInsets = WindowInsets(0),
        plain = true
    ) {
        QrScanner(
            onScan = { payload ->
                onScan(payload)
                sheet.dismiss(onDismiss)
            },
            onClose = { sheet.dismiss(onDismiss) }
        )
    }
}

/** What the scanner says while it can't show the camera. */
internal enum class CameraProblem { Denied, Unavailable }

@Composable
internal fun QrScanner(
    onScan: (PairPayload) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var problem by remember { mutableStateOf<CameraProblem?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) problem = CameraProblem.Denied
    }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraPreview(
                onCode = { raw -> PairPayload.parse(raw)?.let(onScan) != null },
                onSetupFailure = { problem = CameraProblem.Unavailable },
                modifier = Modifier.fillMaxSize()
            )
        }
        ScannerChrome(problem, onClose)
    }
}

@Composable
internal fun ScannerChrome(
    problem: CameraProblem?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val material = LpmTheme.colors.systemBackground.copy(alpha = 0.75f)
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.fillMaxSize()) {
        if (problem == CameraProblem.Unavailable) {
            Text(
                stringResource(R.string.scanner_unavailable),
                style = LpmTheme.typography.footnote,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.4f), shape)
                    .padding(16.dp)
            )
        }
        Icon(
            painter = painterResource(DesignR.drawable.ic_cancel_fill),
            contentDescription = stringResource(R.string.close),
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(30.dp)
                .clickable(role = Role.Button, onClick = onClose)
        )
        Text(
            stringResource(
                if (problem ==
                    CameraProblem.Denied
                ) {
                    R.string.scanner_denied
                } else {
                    R.string.scanner_hint
                }
            ),
            style = LpmTheme.typography.footnote,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .background(material, shape)
                .padding(16.dp)
        )
    }
}
