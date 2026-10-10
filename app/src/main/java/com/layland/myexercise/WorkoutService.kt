package com.layland.myexercise

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.*

class WorkoutService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var speech: SpeechCoach
    private lateinit var engine: WorkoutEngine
    private lateinit var countWords: KoreanCountWords
    private var ticker: Job? = null
    private var breathing: BreathingSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var nextTick = 0L

    override fun onCreate() {
        super.onCreate()
        engine = WorkoutEngine(PreferencesSettings(this).load())
        speech = AndroidSpeechCoach(this)
        countWords = KoreanCountWords(resources.getStringArray(R.array.count_ones).toList(),
            resources.getStringArray(R.array.count_tens).toList(), resources.getStringArray(R.array.count_prefixes).toList(),
            getString(R.string.count_hundred), getString(R.string.count_thousand))
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START -> {
                if (engine.state.phase == Phase.READY || engine.state.phase == Phase.DONE) {
                    engine = WorkoutEngine(PreferencesSettings(this).load())
                    engine.start()
                    breathing = if (engine.config.mode == ExerciseMode.BREATHING) BreathingSession(
                        engine, speech, countWords::format, SystemClock::elapsedRealtime,
                        { deadline -> delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)) }, ::publish,
                    ) else null
                    startForeground(NOTIFICATION_ID, notification())
                    acquireWakeLock()
                    publish()
                    if (breathing == null) speech.speak(engine.config.startPhrase)
                    launchTicker()
                }
            }
            PAUSE -> {
                engine.pause(); ticker?.cancel(); speech.stop(); releaseWakeLock(); publish()
            }
            RESUME -> if (engine.state.paused) {
                engine.resume(); acquireWakeLock(); publish(); launchTicker()
            }
            RESET -> {
                ticker?.cancel(); speech.stop(); engine.reset(); WorkoutSession.current.value = engine.state
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun launchTicker() {
        ticker?.cancel()
        nextTick = SystemClock.elapsedRealtime() + WorkoutTiming.TICK_MS
        ticker = scope.launch {
            val session = breathing
            if (session != null) {
                session.run()
                releaseWakeLock()
                return@launch
            }
            while (isActive && engine.state.phase != Phase.DONE) {
                delay((nextTick - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                val before = engine.state
                val after = engine.tick()
                nextTick += WorkoutTiming.TICK_MS
                publish()
                voiceCues(engine.config, before, after).forEach { cue ->
                    val text = when (cue) {
                        is VoiceCue.Phrase -> cue.text
                        is VoiceCue.Count -> countWords.format(cue.number)
                        is VoiceCue.Remaining -> getString(R.string.seconds, cue.seconds)
                    }
                    speech.speak(text)
                }
            }
            releaseWakeLock()
            // Keep the service alive until reset or restart so the final TTS can finish.
        }
    }
    private fun publish() {
        WorkoutSession.current.value = engine.state
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        }
    }
    private fun notification(): Notification {
        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val reset = PendingIntent.getService(this, 1, Intent(this, WorkoutService::class.java).setAction(RESET), PendingIntent.FLAG_IMMUTABLE)
        val state = engine.state
        val phase = getString(if (state.paused) R.string.paused else phaseLabel(state.phase, engine.config.mode))
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(if (engine.config.mode == ExerciseMode.BREATHING) R.string.breathing_notification_status else R.string.notification_status, phase, state.set, state.remaining))
            .setContentIntent(activity).setOnlyAlertOnce(true)
            .setOngoing(state.phase != Phase.DONE)
            .addAction(Notification.Action.Builder(null, getString(R.string.reset), reset).build()).build()
    }
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG)
        val maximumMs = ((engine.config.workSeconds.toLong() + engine.config.restSeconds) * WorkoutTiming.TICK_MS +
            2 * SpeechTiming.PLAYBACK_TIMEOUT_MS) * engine.config.sets + WAKE_MARGIN_MS
        wakeLock?.acquire(maximumMs)
    }
    private fun releaseWakeLock() { if (wakeLock?.isHeld == true) wakeLock?.release(); wakeLock = null }
    override fun onDestroy() {
        scope.cancel(); releaseWakeLock(); speech.close()
        WorkoutSession.current.value = WorkoutState()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val START = "com.layland.myexercise.START"
        const val PAUSE = "com.layland.myexercise.PAUSE"
        const val RESUME = "com.layland.myexercise.RESUME"
        const val RESET = "com.layland.myexercise.RESET"
        private const val CHANNEL = "workout_timer"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_MARGIN_MS = 60_000L
        private const val WAKE_TAG = "MyExercise:Timer"
    }
}

fun phaseLabel(phase: Phase, mode: ExerciseMode = ExerciseMode.STANDARD) = when (phase) {
    Phase.READY -> R.string.ready
    Phase.WORK -> if (mode == ExerciseMode.BREATHING) R.string.inhale else R.string.work
    Phase.REST -> if (mode == ExerciseMode.BREATHING) R.string.exhale else R.string.rest
    Phase.DONE -> R.string.done
}
