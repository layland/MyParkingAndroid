package com.layland.myexercise

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
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
                           modifier: Modifier = Modifier, validityChanged: (Boolean) -> Unit, save: (WorkoutConfig) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(config.mode) }
    val breathing = mode == ExerciseMode.BREATHING
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
    val enabled = state.phase == Phase.READY || state.phase == Phase.DONE
    val focus = LocalFocusManager.current
    val defaultStart = stringResource(R.string.default_start)
    val defaultRest = stringResource(R.string.default_rest)
    val defaultFinish = stringResource(R.string.default_finish)
    val defaultInhale = stringResource(R.string.default_inhale)
    val defaultExhale = stringResource(R.string.default_exhale)
    val defaultBreathingFinish = stringResource(R.string.default_breathing_finish)
    val validWork = (work.toIntOrNull() ?: -1) in 1..WorkoutConfig.MAX_SECONDS
    val validRest = (rest.toIntOrNull() ?: -1) in (if (breathing) 1 else 0)..WorkoutConfig.MAX_SECONDS
    val validSets = (sets.toIntOrNull() ?: -1) in 1..WorkoutConfig.MAX_SETS
    val validCountdown = (countdown.toIntOrNull() ?: -1) in 0..WorkoutConfig.MAX_COUNTDOWN
    val validEvery = (every.toIntOrNull() ?: -1) in 0..WorkoutConfig.MAX_SECONDS
    // Persist in the input event, so an immediate Start reads the same configuration.
    fun currentDraft() = WorkoutConfig(work.toIntOrNull() ?: -1, rest.toIntOrNull() ?: -1,
        sets.toIntOrNull() ?: -1, countdown.toIntOrNull() ?: -1, every.toIntOrNull() ?: -1,
        start, restPhrase, finish, encouragement, mode)
    fun persistDraft() {
        val candidate = currentDraft()
        validityChanged(candidate.valid())
        if (enabled && candidate.valid() && candidate != config) save(candidate)
    }
    // Also reconcile a draft restored after an Activity recreation.
    SideEffect { persistDraft() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UiSpace.gap)) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .padding(UiSpace.segmentedPadding).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(UiSpace.small)) {
            listOf(R.string.tab_workout, R.string.tab_voice).forEachIndexed { index, label ->
                val selected = selectedTab == index
                Surface(Modifier.weight(1f).selectable(selected = selected, role = Role.Tab,
                    onClick = { selectedTab = index; focus.clearFocus() }), shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant) {
                    Box(Modifier.heightIn(min = UiSpace.touchTarget).padding(UiSpace.small), contentAlignment = Alignment.Center) {
                        Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        val scrollState = key(selectedTab) { rememberScrollState() }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState).padding(bottom = UiSpace.gap),
            verticalArrangement = Arrangement.spacedBy(UiSpace.gap)) {
            if (voice == VoiceStatus.ERROR) Text(stringResource(R.string.tts_error), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
            if (!enabled) Text(stringResource(R.string.edit_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selectedTab == 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiSpace.gap)) {
                    ExerciseMode.entries.forEach { choice ->
                        FilterChip(selected = mode == choice, enabled = enabled,
                            onClick = {
                                if (mode != choice) {
                                    mode = choice
                                    val presetBreathing = choice == ExerciseMode.BREATHING
                                    work = (if (presetBreathing) WorkoutConfig.DEFAULT_INHALE else WorkoutConfig.DEFAULT_WORK).toString()
                                    rest = (if (presetBreathing) WorkoutConfig.DEFAULT_EXHALE else WorkoutConfig.DEFAULT_REST).toString()
                                    start = if (presetBreathing) defaultInhale else defaultStart
                                    restPhrase = if (presetBreathing) defaultExhale else defaultRest
                                    finish = if (presetBreathing) defaultBreathingFinish else defaultFinish
                                    countdown = WorkoutConfig.DEFAULT_COUNTDOWN.toString()
                                    every = WorkoutConfig.DEFAULT_ENCOURAGE.toString()
                                    persistDraft()
                                    focus.clearFocus()
                                }
                            }, label = { Text(stringResource(if (choice == ExerciseMode.BREATHING) R.string.breathing_mode else R.string.standard_mode)) })
                    }
                }
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(UiSpace.cardRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(horizontal = UiSpace.screen, vertical = UiSpace.small)) {
                        NumericRow(if (breathing) R.string.inhale_duration else R.string.duration_work, null, work, enabled, validWork, icon = if (breathing) R.drawable.ic_inhale else R.drawable.ic_work) { work = it; persistDraft() }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NumericRow(if (breathing) R.string.exhale_duration else R.string.duration_rest, null, rest, enabled, validRest, icon = if (breathing) R.drawable.ic_exhale else R.drawable.ic_rest) { rest = it; persistDraft() }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NumericRow(if (breathing) R.string.repeat_count else R.string.sets, null, sets, enabled, validSets, if (breathing) R.string.unit_repeats else R.string.unit_sets, R.drawable.ic_repeat) { sets = it; persistDraft() }
                    }
                }
                if (breathing) Text(stringResource(R.string.breathing_count_help), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                if (breathing) {
                    Text(stringResource(R.string.breathing_count_help), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else SettingsCard(R.string.voice_timing, R.string.voice_timing_help) {
                    NumericRow(R.string.countdown_short, R.string.countdown_help, countdown, enabled, validCountdown) { countdown = it; persistDraft() }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NumericRow(R.string.encourage_short, R.string.encourage_help, every, enabled, validEvery) { every = it; persistDraft() }
                }
                SettingsCard(R.string.voice_settings, R.string.phrases_help) {
                    PhraseField(if (breathing) R.string.inhale_phrase else R.string.start_phrase, start, enabled) { start = it; persistDraft() }
                    PhraseField(if (breathing) R.string.exhale_phrase else R.string.rest_phrase, restPhrase, enabled) { restPhrase = it; persistDraft() }
                    if (!breathing) PhraseField(R.string.encourage_phrase, encouragement, enabled) { encouragement = it; persistDraft() }
                    PhraseField(R.string.finish_phrase, finish, enabled) { finish = it; persistDraft() }
                }
                if (state.phase != Phase.READY && voice != VoiceStatus.ERROR) Text(stringResource(when (voice) {
                    VoiceStatus.LOADING -> R.string.tts_loading
                    VoiceStatus.READY -> R.string.tts_ready
                    VoiceStatus.ERROR -> R.string.tts_error
                }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(if (currentDraft().valid()) R.string.auto_saved
                else if (breathing) R.string.breathing_invalid else R.string.invalid),
                style = MaterialTheme.typography.bodySmall,
                color = if (currentDraft().valid()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            if (selectedTab == 0) Text(stringResource(if (config.mode == ExerciseMode.BREATHING) R.string.breathing_summary else R.string.workout_summary, config.workSeconds, config.restSeconds, config.sets),
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun NumericRow(label: Int, help: Int?, value: String, enabled: Boolean, valid: Boolean,
                       unit: Int = R.string.unit_seconds, icon: Int? = null, change: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = UiSpace.rowHeight).padding(vertical = UiSpace.small)) {
        val stack = maxWidth / LocalDensity.current.fontScale < UiSpace.pairBreakpoint
        val description: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpace.gap)) {
                if (icon != null) Icon(painterResource(icon), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(UiSpace.icon))
                Column(verticalArrangement = Arrangement.spacedBy(UiSpace.small)) {
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                    if (help != null) Text(stringResource(help), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
    val focus = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(UiSpace.fieldRadius)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpace.small)) {
        BasicTextField(value, onValueChange = change, enabled = enabled, singleLine = true,
            modifier = Modifier.width(UiSpace.numberWidth * LocalDensity.current.fontScale)
                .heightIn(min = UiSpace.touchTarget).background(scheme.background, shape)
                .border(UiSpace.inputBorder, if (valid) scheme.outlineVariant else scheme.error, shape)
                .semantics { contentDescription = accessibleLabel },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            cursorBrush = SolidColor(scheme.primary),
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center,
                color = if (!valid) scheme.error else if (enabled) scheme.onSurface else scheme.onSurfaceVariant),
            decorationBox = { inner ->
                Box(Modifier.heightIn(min = UiSpace.touchTarget).padding(horizontal = UiSpace.small, vertical = UiSpace.small),
                    contentAlignment = Alignment.Center) { inner() }
            })
        Text(stringResource(unit), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun PhraseField(label: Int, value: String, enabled: Boolean, change: (String) -> Unit) {
    OutlinedTextField(value, onValueChange = change, enabled = enabled, label = { Text(stringResource(label)) },
        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, shape = RoundedCornerShape(UiSpace.fieldRadius))
}
