package com.layland.myexercise

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        val repository: SettingsRepository = PreferencesSettings(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { WorkoutScreen(repository, ::sendCommand) }
            }
        }
    }
    private fun sendCommand(action: String) {
        val intent = Intent(this, WorkoutService::class.java).setAction(action)
        if (action == WorkoutService.START) startForegroundService(intent) else startService(intent)
    }
}

private object Spacing { val screen = 24.dp; val gap = 12.dp; val section = 20.dp }

@Composable
private fun WorkoutScreen(repository: SettingsRepository, command: (String) -> Unit) {
    var config by remember { mutableStateOf(repository.load()) }
    val state by WorkoutSession.state.collectAsStateWithLifecycle()
    val voice by WorkoutSession.voiceStatus.collectAsStateWithLifecycle()
    val editable = state.phase == Phase.READY || state.phase == Phase.DONE
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding()
            .padding(horizontal = Spacing.screen, vertical = Spacing.gap),
        verticalArrangement = Arrangement.spacedBy(Spacing.gap),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(Spacing.gap), horizontalArrangement = Arrangement.spacedBy(Spacing.gap)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.gap)) {
                    Text(stringResource(if (state.paused) R.string.paused else phaseLabel(state.phase)), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.seconds, if (state.phase == Phase.READY) config.workSeconds else state.remaining), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.set_progress, state.set, config.sets))
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.gap)) {
                    Button(onClick = { command(if (editable) WorkoutService.START else if (state.paused) WorkoutService.RESUME else WorkoutService.PAUSE) }) {
                        Text(stringResource(if (editable) R.string.start else if (state.paused) R.string.resume else R.string.pause))
                    }
                    OutlinedButton(onClick = { command(WorkoutService.RESET) }, enabled = state.phase != Phase.READY) { Text(stringResource(R.string.reset)) }
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.section),
            verticalArrangement = Arrangement.spacedBy(Spacing.gap),
        ) {
            Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
            if (state.phase != Phase.READY) Text(stringResource(when (voice) {
                VoiceStatus.LOADING -> R.string.tts_loading
                VoiceStatus.READY -> R.string.tts_ready
                VoiceStatus.ERROR -> R.string.tts_error
            }), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.edit_hint), style = MaterialTheme.typography.bodySmall)
            SettingsForm(config, editable) { repository.save(it); config = it }
        }
    }
}

@Composable
private fun SettingsForm(config: WorkoutConfig, enabled: Boolean, save: (WorkoutConfig) -> Unit) {
    var work by rememberSaveable { mutableStateOf(config.workSeconds.toString()) }
    var rest by rememberSaveable { mutableStateOf(config.restSeconds.toString()) }
    var sets by rememberSaveable { mutableStateOf(config.sets.toString()) }
    var countdown by rememberSaveable { mutableStateOf(config.countdownSeconds.toString()) }
    var every by rememberSaveable { mutableStateOf(config.encourageEvery.toString()) }
    var start by rememberSaveable { mutableStateOf(config.startPhrase) }
    var restPhrase by rememberSaveable { mutableStateOf(config.restPhrase) }
    var finish by rememberSaveable { mutableStateOf(config.finishPhrase) }
    var encouragement by rememberSaveable { mutableStateOf(config.encouragePhrase) }
    var message by remember { mutableIntStateOf(0) }
    Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
    SettingField(R.string.work_seconds, work, enabled, true) { work = it; message = 0 }
    SettingField(R.string.rest_seconds, rest, enabled, true) { rest = it; message = 0 }
    SettingField(R.string.sets, sets, enabled, true) { sets = it; message = 0 }
    SettingField(R.string.countdown, countdown, enabled, true) { countdown = it; message = 0 }
    SettingField(R.string.encourage_every, every, enabled, true) { every = it; message = 0 }
    Text(stringResource(R.string.voice_settings), style = MaterialTheme.typography.titleLarge)
    SettingField(R.string.start_phrase, start, enabled) { start = it; message = 0 }
    SettingField(R.string.rest_phrase, restPhrase, enabled) { restPhrase = it; message = 0 }
    SettingField(R.string.finish_phrase, finish, enabled) { finish = it; message = 0 }
    SettingField(R.string.encourage_phrase, encouragement, enabled) { encouragement = it; message = 0 }
    Button(enabled = enabled, onClick = {
        val candidate = WorkoutConfig(work.toIntOrNull() ?: -1, rest.toIntOrNull() ?: -1, sets.toIntOrNull() ?: -1,
            countdown.toIntOrNull() ?: -1, every.toIntOrNull() ?: -1, start, restPhrase, finish, encouragement)
        if (candidate.valid()) { save(candidate); message = R.string.saved } else message = R.string.invalid
    }) { Text(stringResource(R.string.save)) }
    if (message != 0) Text(stringResource(message), color = if (message == R.string.invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingField(label: Int, value: String, enabled: Boolean, numeric: Boolean = false, change: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = change, enabled = enabled, label = { Text(stringResource(label)) },
        modifier = Modifier.fillMaxWidth(), singleLine = numeric,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text))
}
