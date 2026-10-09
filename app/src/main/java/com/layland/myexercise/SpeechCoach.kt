package com.layland.myexercise

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SpeechCoach { fun speak(text: String); fun stop(); fun close() }
enum class VoiceStatus { LOADING, READY, ERROR }

class AndroidSpeechCoach(context: Context) : SpeechCoach {
    private var ready = false
    private var pending: String? = null
    private var engine: TextToSpeech? = null
    init {
        WorkoutSession.voice.value = VoiceStatus.LOADING
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = engine?.setLanguage(Locale.KOREAN)
                ready = result != null && result >= TextToSpeech.LANG_AVAILABLE
            }
            WorkoutSession.voice.value = if (ready) VoiceStatus.READY else VoiceStatus.ERROR
            if (ready) pending?.let { speak(it) }
            pending = null
        }
    }
    override fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) { pending = text; return }
        engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "workout_voice")
    }
    override fun stop() { pending = null; engine?.stop() }
    override fun close() { stop(); engine?.shutdown() }
}

object WorkoutSession {
    internal val current = MutableStateFlow(WorkoutState())
    val state = current.asStateFlow()
    internal val voice = MutableStateFlow(VoiceStatus.LOADING)
    val voiceStatus = voice.asStateFlow()
}
