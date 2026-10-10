package com.layland.myexercise

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SpeechCoach {
    fun speak(text: String, enqueue: Boolean = false)
    suspend fun playAndWait(text: String): Boolean
    fun stop()
    fun close()
}
enum class VoiceStatus { LOADING, READY, ERROR }

class AndroidSpeechCoach(context: Context) : SpeechCoach {
    private val ready = CompletableDeferred<Boolean>()
    private val playback = SpeechPlaybackRegistry()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var closed = false
    private var engine: TextToSpeech? = null
    init {
        WorkoutSession.voice.value = VoiceStatus.LOADING
        val handler = Handler(Looper.getMainLooper())
        engine = TextToSpeech(context.applicationContext) { status ->
            // The posted callback runs after the engine field and progress listener are assigned.
            handler.post {
                if (!closed) {
                    engine?.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    val language = if (status == TextToSpeech.SUCCESS) engine?.setLanguage(Locale.KOREAN) else null
                    val available = language != null && language >= TextToSpeech.LANG_AVAILABLE
                    WorkoutSession.voice.value = if (available) VoiceStatus.READY else VoiceStatus.ERROR
                    ready.complete(available)
                }
            }
        }
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                playback.started(utteranceId)
                if (!closed) WorkoutSession.voice.value = VoiceStatus.READY
            }
            override fun onDone(utteranceId: String) { playback.finish(utteranceId, true) }
            @Deprecated("Platform callback retained for older engines")
            override fun onError(utteranceId: String) { failed(utteranceId) }
            override fun onError(utteranceId: String, errorCode: Int) { failed(utteranceId) }
            override fun onStop(utteranceId: String, interrupted: Boolean) { playback.finish(utteranceId, false) }
        })
    }
    private fun failed(id: String) {
        playback.finish(id, false)
        if (!closed) WorkoutSession.voice.value = VoiceStatus.ERROR
    }
    private suspend fun awaitReady(): Boolean {
        val available = withTimeoutOrNull(SpeechTiming.INIT_TIMEOUT_MS) { ready.await() } ?: false
        if (!available && !closed) WorkoutSession.voice.value = VoiceStatus.ERROR
        return available && !closed
    }
    private fun request(text: String, enqueue: Boolean): SpeechPlayback {
        val ticket = playback.register()
        val result = engine?.speak(text, if (enqueue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH, null, ticket.id)
        if (result != TextToSpeech.SUCCESS) failed(ticket.id)
        return ticket
    }
    override fun speak(text: String, enqueue: Boolean) {
        if (text.isBlank() || closed) return
        scope.launch { if (awaitReady()) request(text, enqueue) }
    }
    override suspend fun playAndWait(text: String): Boolean {
        if (text.isBlank()) return true
        if (!awaitReady()) return false
        val ticket = request(text, enqueue = false)
        return try {
            withTimeoutOrNull(SpeechTiming.PLAYBACK_TIMEOUT_MS) { ticket.finished.await() } ?: run {
                failed(ticket.id)
                false
            }
        } finally {
            // Cancellation from pause/reset releases the outstanding wait without marking an engine error.
            playback.finish(ticket.id, false)
        }
    }
    override fun stop() {
        scope.coroutineContext.cancelChildren()
        playback.stopAll()
        engine?.stop()
    }
    override fun close() {
        closed = true
        ready.complete(false)
        stop(); scope.cancel(); engine?.shutdown()
    }

}

object WorkoutSession {
    internal val current = MutableStateFlow(WorkoutState())
    val state = current.asStateFlow()
    internal val voice = MutableStateFlow(VoiceStatus.LOADING)
    val voiceStatus = voice.asStateFlow()
}

internal object SpeechTiming {
    const val INIT_TIMEOUT_MS = 10_000L
    const val PLAYBACK_TIMEOUT_MS = 60_000L
}
