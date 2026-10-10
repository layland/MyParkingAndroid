package com.layland.myexercise

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SpeechCoach { fun speak(text: String, enqueue: Boolean = false); fun stop(); fun close() }
enum class VoiceStatus { LOADING, READY, ERROR }

class AndroidSpeechCoach(context: Context) : SpeechCoach {
    private var ready = false
    private var initialized = false
    private val pending = ArrayDeque<Pair<String, Boolean>>()
    private var engine: TextToSpeech? = null
    init {
        WorkoutSession.voice.value = VoiceStatus.LOADING
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = engine?.setLanguage(Locale.KOREAN)
                ready = result != null && result >= TextToSpeech.LANG_AVAILABLE
            }
            initialized = true
            WorkoutSession.voice.value = if (ready) VoiceStatus.READY else VoiceStatus.ERROR
            val buffered = pending.toList()
            pending.clear()
            if (ready) buffered.forEach { (text, enqueue) -> speak(text, enqueue) }
        }
    }
    override fun speak(text: String, enqueue: Boolean) {
        if (text.isBlank()) return
        if (!ready) {
            if (initialized) return
            if (!enqueue) pending.clear()
            pending.addLast(text to enqueue)
            return
        }
        engine?.speak(text, if (enqueue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH, null, "workout_voice")
    }
    override fun stop() { pending.clear(); engine?.stop() }
    override fun close() { stop(); engine?.shutdown() }
}

object WorkoutSession {
    internal val current = MutableStateFlow(WorkoutState())
    val state = current.asStateFlow()
    internal val voice = MutableStateFlow(VoiceStatus.LOADING)
    val voiceStatus = voice.asStateFlow()
}
