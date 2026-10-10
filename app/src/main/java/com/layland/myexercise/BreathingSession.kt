package com.layland.myexercise

/** Starts each interval only after its instruction has finished playing. */
internal class BreathingSession(
    private val engine: WorkoutEngine,
    private val speech: SpeechCoach,
    private val countWord: (Int) -> String,
    private val now: () -> Long,
    private val waitUntil: suspend (Long) -> Unit,
    private val publish: () -> Unit,
) {
    private var announced = false
    suspend fun run() {
        while (engine.state.phase != Phase.DONE && !engine.state.paused) {
            val phase = engine.state.phase
            val set = engine.state.set
            if (!announced) {
                speech.playAndWait(if (phase == Phase.WORK) engine.config.startPhrase else engine.config.restPhrase)
                announced = true
            }
            var deadline = now() + WorkoutTiming.TICK_MS
            while (engine.state.phase == phase && engine.state.set == set && !engine.state.paused) {
                waitUntil(deadline)
                val before = engine.state
                val after = engine.tick()
                if (after.phase != phase || after.set != set) announced = false
                publish()
                // Each short number finishes before the next request; no shared queue or utterance ID.
                voiceCues(engine.config, before, after).forEach { cue ->
                    if (cue is VoiceCue.Count) speech.playAndWait(countWord(cue.number))
                }
                deadline += WorkoutTiming.TICK_MS
            }
        }
        if (engine.state.phase == Phase.DONE) speech.playAndWait(engine.config.finishPhrase)
    }
}

internal object WorkoutTiming { const val TICK_MS = 1000L }
