package com.layland.myexercise

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

@Composable
internal fun SettingsPanel(config: WorkoutConfig, state: WorkoutState, voice: VoiceStatus,
                           modifier: Modifier = Modifier, save: (WorkoutConfig) -> Unit) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
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
    val enabled = state.phase == Phase.READY || state.phase == Phase.DONE
    val focus = LocalFocusManager.current
    val validWork = (work.toIntOrNull() ?: -1) in 1..WorkoutConfig.MAX_SECONDS
    val validRest = (rest.toIntOrNull() ?: -1) in 0..WorkoutConfig.MAX_SECONDS
    val validSets = (sets.toIntOrNull() ?: -1) in 1..WorkoutConfig.MAX_SETS
    val validCountdown = (countdown.toIntOrNull() ?: -1) in 0..WorkoutConfig.MAX_COUNTDOWN
    val validEvery = (every.toIntOrNull() ?: -1) in 0..WorkoutConfig.MAX_SECONDS
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UiSpace.gap)) {
        TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.background) {
            listOf(R.string.tab_workout, R.string.tab_voice).forEachIndexed { index, label ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index; focus.clearFocus() },
                    text = { Text(stringResource(label)) })
            }
        }
        val scrollState = key(selectedTab) { rememberScrollState() }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState).padding(bottom = UiSpace.gap),
            verticalArrangement = Arrangement.spacedBy(UiSpace.gap)) {
            if (!enabled) Text(stringResource(R.string.edit_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selectedTab == 0) {
                SettingsCard(R.string.settings, R.string.workout_help) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth / LocalDensity.current.fontScale >= UiSpace.pairBreakpoint) {
                            Row(horizontalArrangement = Arrangement.spacedBy(UiSpace.section)) {
                                DurationField(R.string.duration_work, work, enabled, validWork, Modifier.weight(1f)) { work = it; message = 0 }
                                DurationField(R.string.duration_rest, rest, enabled, validRest, Modifier.weight(1f)) { rest = it; message = 0 }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(UiSpace.gap)) {
                                NumericRow(R.string.duration_work, R.string.work_range, work, enabled, validWork) { work = it; message = 0 }
                                NumericRow(R.string.duration_rest, R.string.rest_range, rest, enabled, validRest) { rest = it; message = 0 }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NumericRow(R.string.sets, R.string.sets_help, sets, enabled, validSets, R.string.unit_sets) { sets = it; message = 0 }
                }
            } else {
                SettingsCard(R.string.voice_timing, R.string.voice_timing_help) {
                    NumericRow(R.string.countdown_short, R.string.countdown_help, countdown, enabled, validCountdown) { countdown = it; message = 0 }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NumericRow(R.string.encourage_short, R.string.encourage_help, every, enabled, validEvery) { every = it; message = 0 }
                }
                SettingsCard(R.string.voice_settings, R.string.phrases_help) {
                    PhraseField(R.string.start_phrase, start, enabled) { start = it; message = 0 }
                    PhraseField(R.string.rest_phrase, restPhrase, enabled) { restPhrase = it; message = 0 }
                    PhraseField(R.string.encourage_phrase, encouragement, enabled) { encouragement = it; message = 0 }
                    PhraseField(R.string.finish_phrase, finish, enabled) { finish = it; message = 0 }
                }
                if (state.phase != Phase.READY) Text(stringResource(when (voice) {
                    VoiceStatus.LOADING -> R.string.tts_loading
                    VoiceStatus.READY -> R.string.tts_ready
                    VoiceStatus.ERROR -> R.string.tts_error
                }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (message != 0) Text(stringResource(message), style = MaterialTheme.typography.bodySmall,
            color = if (message == R.string.invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Button(enabled = enabled, onClick = {
            val candidate = WorkoutConfig(work.toIntOrNull() ?: -1, rest.toIntOrNull() ?: -1, sets.toIntOrNull() ?: -1,
                countdown.toIntOrNull() ?: -1, every.toIntOrNull() ?: -1, start, restPhrase, finish, encouragement)
            if (candidate.valid()) { save(candidate); focus.clearFocus(); message = R.string.saved }
            else message = R.string.invalid
        }, modifier = Modifier.fillMaxWidth().heightIn(min = UiSpace.buttonHeight), shape = RoundedCornerShape(UiSpace.fieldRadius)) {
            Text(stringResource(R.string.save))
        }
    }
}

@Composable
private fun SettingsCard(title: Int, help: Int, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(UiSpace.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(UiSpace.screen), verticalArrangement = Arrangement.spacedBy(UiSpace.gap)) {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpace.small)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun DurationField(label: Int, value: String, enabled: Boolean, valid: Boolean, modifier: Modifier, change: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(UiSpace.small)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        NumberInput(label, value, enabled, valid, R.string.unit_seconds, change)
    }
}

@Composable
private fun NumericRow(label: Int, help: Int, value: String, enabled: Boolean, valid: Boolean,
                       unit: Int = R.string.unit_seconds, change: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stack = maxWidth / LocalDensity.current.fontScale < UiSpace.pairBreakpoint
        val description: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpace.small)) {
                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpace.small)) {
                description()
                NumberInput(label, value, enabled, valid, unit, change)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpace.gap)) {
                Box(Modifier.weight(1f)) { description() }
                NumberInput(label, value, enabled, valid, unit, change)
            }
        }
    }
}

@Composable
private fun NumberInput(label: Int, value: String, enabled: Boolean, valid: Boolean, unit: Int, change: (String) -> Unit) {
    val accessibleLabel = stringResource(label)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpace.small)) {
        OutlinedTextField(value, onValueChange = change, enabled = enabled, singleLine = true, isError = !valid,
            modifier = Modifier.width(UiSpace.numberWidth * LocalDensity.current.fontScale)
                .semantics { contentDescription = accessibleLabel },
            shape = RoundedCornerShape(UiSpace.fieldRadius), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.titleMedium,
            // The external label remains visible; semantics names the short input for screen readers.
            label = null)
        Text(stringResource(unit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhraseField(label: Int, value: String, enabled: Boolean, change: (String) -> Unit) {
    OutlinedTextField(value, onValueChange = change, enabled = enabled, label = { Text(stringResource(label)) },
        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, shape = RoundedCornerShape(UiSpace.fieldRadius))
}
