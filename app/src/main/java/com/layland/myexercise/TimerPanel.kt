package com.layland.myexercise

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily

private const val SECONDS_PER_MINUTE = 60

@Composable
internal fun TimerPanel(config: WorkoutConfig, state: WorkoutState, command: (String) -> Unit,
                        compact: Boolean, modifier: Modifier = Modifier) {
    val editable = state.phase == Phase.READY || state.phase == Phase.DONE
    val rest = state.phase == Phase.REST
    val accent = if (rest) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val container = if (rest) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    val foreground = if (rest) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    val remaining = if (state.phase == Phase.READY) config.workSeconds else state.remaining
    val duration = if (rest) config.restSeconds else config.workSeconds
    val progress = when (state.phase) {
        Phase.READY -> 0f
        Phase.DONE -> 1f
        else -> (1f - state.remaining.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f)
    }
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(UiSpace.cardRadius),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = foreground)) {
        Column(Modifier.padding(if (compact) UiSpace.gap else UiSpace.section),
            verticalArrangement = Arrangement.spacedBy(UiSpace.gap)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = accent, contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(UiSpace.fieldRadius)) {
                    Text(stringResource(if (state.paused) R.string.paused else phaseLabel(state.phase)),
                        Modifier.padding(horizontal = UiSpace.gap, vertical = UiSpace.small), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.set_progress, state.set, config.sets), style = MaterialTheme.typography.labelLarge)
            }
            Text(stringResource(R.string.timer_clock, remaining / SECONDS_PER_MINUTE, remaining % SECONDS_PER_MINUTE),
                style = if (compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            if (!compact) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(),
                color = accent, trackColor = MaterialTheme.colorScheme.surface)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiSpace.gap)) {
                Button(onClick = { command(if (editable) WorkoutService.START else if (state.paused) WorkoutService.RESUME else WorkoutService.PAUSE) },
                    modifier = Modifier.weight(1f).heightIn(min = UiSpace.buttonHeight),
                    shape = RoundedCornerShape(UiSpace.fieldRadius),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                    Icon(painterResource(if (!editable && !state.paused) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = null, modifier = Modifier.size(UiSpace.icon))
                    Spacer(Modifier.width(UiSpace.small))
                    Text(stringResource(if (editable) R.string.start else if (state.paused) R.string.resume else R.string.pause))
                }
                OutlinedButton(onClick = { command(WorkoutService.RESET) }, enabled = state.phase != Phase.READY,
                    modifier = Modifier.heightIn(min = UiSpace.buttonHeight), shape = RoundedCornerShape(UiSpace.fieldRadius)) {
                    Text(stringResource(R.string.reset))
                }
            }
        }
    }
}
