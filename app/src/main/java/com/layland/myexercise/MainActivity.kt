package com.layland.myexercise

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.graphics.Color
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        val repository: SettingsRepository = PreferencesSettings(this)
        setContent {
            ExerciseTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { WorkoutScreen(repository, ::sendCommand) }
            }
        }
    }
    private fun sendCommand(action: String) {
        val intent = Intent(this, WorkoutService::class.java).setAction(action)
        if (action == WorkoutService.START) startForegroundService(intent) else startService(intent)
    }
}

@Composable
private fun WorkoutScreen(repository: SettingsRepository, command: (String) -> Unit) {
    var config by remember { mutableStateOf(repository.load()) }
    var settingsValid by remember { mutableStateOf(true) }
    val dispatch: (String) -> Unit = { action ->
        if (action != WorkoutService.START || settingsValid) command(action)
    }
    val state by WorkoutSession.state.collectAsStateWithLifecycle()
    val voice by WorkoutSession.voiceStatus.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(UiSpace.screen)) {
        val wide = maxWidth / LocalDensity.current.fontScale >= UiSpace.wideBreakpoint
        val compact = maxHeight / LocalDensity.current.fontScale < UiSpace.compactHeight
        Column(verticalArrangement = Arrangement.spacedBy(UiSpace.gap)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpace.gap)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(UiSpace.brandIcon))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                }
                Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (wide) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(UiSpace.section)) {
                    TimerPanel(config, state, dispatch, compact, Modifier.weight(1f), settingsValid)
                    SettingsPanel(config, state, voice, Modifier.weight(1f).fillMaxHeight(), validityChanged = { settingsValid = it }) { repository.save(it); config = it }
                }
            } else {
                TimerPanel(config, state, dispatch, compact, canStart = settingsValid)
                SettingsPanel(config, state, voice, Modifier.weight(1f), validityChanged = { settingsValid = it }) { repository.save(it); config = it }
            }
        }
    }
}
