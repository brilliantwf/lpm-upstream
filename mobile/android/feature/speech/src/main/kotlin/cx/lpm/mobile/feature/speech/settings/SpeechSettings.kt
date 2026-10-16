package cx.lpm.mobile.feature.speech.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.data.prefs.SpeechPrefs
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ButtonRow
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.PickerRow
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.speech.R
import cx.lpm.mobile.feature.speech.engine.SpeechVoice
import cx.lpm.mobile.feature.speech.ui.rateLabel

/** Settings → Read aloud's sections, for the page the Settings sheet pushes. */
@Composable
fun SpeechSettings(
    modifier: Modifier = Modifier,
    viewModel: SpeechSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SpeechSettingsContent(
        state = state,
        onEngineChange = viewModel::setEngine,
        onRateChange = viewModel::setRate,
        onVoiceSelect = viewModel::selectVoice,
        onPlaySample = viewModel::playSample,
        modifier = modifier
    )
}

/** The engine, the speed and a sample, then the phone's voices when it does the speaking. */
@Composable
fun SpeechSettingsContent(
    state: SpeechSettingsUiState,
    onEngineChange: (String) -> Unit,
    onRateChange: (Double) -> Unit,
    onVoiceSelect: (String) -> Unit,
    onPlaySample: () -> Unit,
    modifier: Modifier = Modifier
) {
    val device = stringResource(R.string.speech_engine_device, state.deviceNoun)
    val mac = stringResource(R.string.speech_engine_mac)
    val engine = if (state.usesDevice) SpeechPrefs.ENGINE_DEVICE else SpeechPrefs.ENGINE_MAC
    Column(modifier) {
        ListSection(
            footer = if (state.usesDevice) {
                stringResource(R.string.speech_engine_device_footer, state.deviceNoun)
            } else {
                stringResource(R.string.speech_engine_mac_footer)
            }
        ) {
            PickerRow(
                title = stringResource(R.string.speech_engine),
                options = listOf(SpeechPrefs.ENGINE_DEVICE, SpeechPrefs.ENGINE_MAC),
                selected = engine,
                onSelect = onEngineChange,
                optionLabel = { if (it == SpeechPrefs.ENGINE_MAC) mac else device }
            )
        }
        ListSection(footer = stringResource(R.string.speech_speed_footer)) {
            PickerRow(
                title = stringResource(R.string.speech_speed),
                options = SpeechPrefs.RATES,
                selected = state.prefs.rate,
                onSelect = onRateChange,
                optionLabel = ::rateLabel
            )
            ButtonRow(
                text = stringResource(R.string.speech_hear_sample),
                onClick = onPlaySample,
                icon = painterResource(DesignR.drawable.ic_play_circle)
            )
        }
        if (state.usesDevice) {
            ListSection(
                header = stringResource(R.string.speech_voice),
                headerStyle = ListHeaderStyle.Title
            ) {
                VoiceRow(
                    name = stringResource(R.string.speech_best_available),
                    quality = null,
                    selected = state.prefs.voiceId.isEmpty(),
                    onClick = { onVoiceSelect("") }
                )
                state.voices.forEach { voice ->
                    VoiceRow(
                        name = voice.name,
                        quality = voice.quality.label(),
                        selected = state.prefs.voiceId == voice.id,
                        onClick = { onVoiceSelect(voice.id) }
                    )
                }
            }
        }
        if (state.usesDevice && state.onlyBasicVoices) {
            ListSection {
                ListRow(
                    leading = {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_graphic_eq),
                            contentDescription = null,
                            tint = LpmTheme.colors.orange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                ) {
                    Text(
                        text = stringResource(R.string.speech_basic_voices),
                        style = LpmTheme.typography.footnote,
                        color = LpmTheme.colors.secondaryLabel
                    )
                }
            }
        }
    }
}

/** An inline picker's row: the voice, its quality in gray, a check when it's chosen. */
@Composable
private fun VoiceRow(name: String, quality: String?, selected: Boolean, onClick: () -> Unit) {
    ListRow(
        onClick = onClick,
        trailing = {
            if (selected) {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_check),
                    contentDescription = null,
                    tint = LpmTheme.colors.tint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    ) {
        Text(text = name, style = LpmTheme.typography.body, color = LpmTheme.colors.label)
        if (quality != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = quality,
                style = LpmTheme.typography.caption1,
                color = LpmTheme.colors.secondaryLabel
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun SpeechVoice.Quality.label(): String? = when (this) {
    SpeechVoice.Quality.Premium -> stringResource(R.string.speech_quality_premium)
    SpeechVoice.Quality.Enhanced -> stringResource(R.string.speech_quality_enhanced)
    SpeechVoice.Quality.Default -> null
}
