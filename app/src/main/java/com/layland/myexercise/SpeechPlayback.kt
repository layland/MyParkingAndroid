package com.layland.myexercise

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred

internal class SpeechPlayback(val id: String) {
    val started = CompletableDeferred<Boolean>()
    val finished = CompletableDeferred<Boolean>()
}

/** Unique IDs prevent late or duplicate engine callbacks from completing another utterance. */
internal class SpeechPlaybackRegistry {
    private val sequence = AtomicLong()
    private val requests = ConcurrentHashMap<String, SpeechPlayback>()
    fun register(): SpeechPlayback = SpeechPlayback("speech-${sequence.incrementAndGet()}").also { requests[it.id] = it }
    fun started(id: String) { requests[id]?.started?.complete(true) }
    fun finish(id: String, success: Boolean) {
        requests.remove(id)?.let {
            it.started.complete(success)
            it.finished.complete(success)
        }
    }
    fun stopAll() { requests.keys.toList().forEach { finish(it, false) } }
}
