package com.layland.myexercise

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity

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
    // A ready timer shows a complete ring; it empties as the current interval passes.
    val progress = when (state.phase) {
        Phase.READY -> 1f
        Phase.DONE -> 0f
        else -> (state.remaining.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f)
    }
    val clock = stringResource(R.string.timer_clock, remaining / SECONDS_PER_MINUTE, remaining % SECONDS_PER_MINUTE)
    val phase: @Composable () -> Unit = {
        Surface(color = container, contentColor = foreground, shape = CircleShape) {
            Text(stringResource(if (state.paused) R.string.paused else phaseLabel(state.phase)),
                Modifier.padding(horizontal = UiSpace.section, vertical = UiSpace.small),
                style = MaterialTheme.typography.labelLarge)
        }
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(UiSpace.gap)) {
        if (compact) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpace.gap)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UiSpace.small)) {
                    phase()
                    Text(stringResource(R.string.set_progress, state.set, config.sets),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(clock, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val ringSize = (UiSpace.ringSize * LocalDensity.current.fontScale).coerceAtMost(maxWidth)
                Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(),
                        color = accent, trackColor = container, strokeWidth = UiSpace.ringStroke)
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(UiSpace.gap)) {
                        phase()
                        Text(clock, fontSize = UiSpace.timerText, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.set_progress, state.set, config.sets),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiSpace.gap),
            verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { command(if (editable) WorkoutService.START else if (state.paused) WorkoutService.RESUME else WorkoutService.PAUSE) },
                modifier = Modifier.weight(1f).heightIn(min = UiSpace.buttonHeight), shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Icon(painterResource(if (!editable && !state.paused) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = null, modifier = Modifier.size(UiSpace.icon))
                Spacer(Modifier.width(UiSpace.gap))
                Text(stringResource(if (editable) R.string.start else if (state.paused) R.string.resume else R.string.pause),
                    style = MaterialTheme.typography.titleMedium)
            }
            FilledTonalIconButton(onClick = { command(WorkoutService.RESET) }, enabled = state.phase != Phase.READY,
                modifier = Modifier.size(UiSpace.buttonHeight),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = container, contentColor = accent)) {
                Icon(painterResource(R.drawable.ic_reset), contentDescription = stringResource(R.string.reset),
                    modifier = Modifier.size(UiSpace.icon))
            }
        }
    }
}
