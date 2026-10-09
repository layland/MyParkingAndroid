package com.layland.myexercise

data class WorkoutConfig(
    val workSeconds: Int = DEFAULT_WORK,
    val restSeconds: Int = DEFAULT_REST,
    val sets: Int = DEFAULT_SETS,
    val countdownSeconds: Int = DEFAULT_COUNTDOWN,
    val encourageEvery: Int = DEFAULT_ENCOURAGE,
    val startPhrase: String,
    val restPhrase: String,
    val finishPhrase: String,
    val encouragePhrase: String,
) {
    fun valid() = workSeconds in 1..MAX_SECONDS && restSeconds in 0..MAX_SECONDS &&
        sets in 1..MAX_SETS && countdownSeconds in 0..MAX_COUNTDOWN && encourageEvery in 0..MAX_SECONDS
    companion object {
        const val DEFAULT_WORK = 30
        const val DEFAULT_REST = 15
        const val DEFAULT_SETS = 3
        const val DEFAULT_COUNTDOWN = 5
        const val DEFAULT_ENCOURAGE = 15
        const val MAX_SECONDS = 3600
        const val MAX_SETS = 100
        const val MAX_COUNTDOWN = 60
    }
}

enum class Phase { READY, WORK, REST, DONE }
data class WorkoutState(val phase: Phase = Phase.READY, val set: Int = 1, val remaining: Int = 0, val paused: Boolean = false)

/** Pure session logic; timing, voice and Android lifecycle are handled by adapters. */
class WorkoutEngine(val config: WorkoutConfig) {
    init { require(config.valid()) }
    var state = WorkoutState()
        private set
    fun start(): WorkoutState { state = WorkoutState(Phase.WORK, remaining = config.workSeconds); return state }
    fun pause() { if (state.phase == Phase.WORK || state.phase == Phase.REST) state = state.copy(paused = true) }
    fun resume() { state = state.copy(paused = false) }
    fun reset() { state = WorkoutState() }
    fun tick(): WorkoutState {
        if (state.paused || state.phase == Phase.READY || state.phase == Phase.DONE) return state
        state = if (state.remaining > 1) state.copy(remaining = state.remaining - 1) else when {
            state.phase == Phase.WORK && state.set == config.sets -> state.copy(phase = Phase.DONE, remaining = 0)
            state.phase == Phase.WORK && config.restSeconds > 0 -> state.copy(phase = Phase.REST, remaining = config.restSeconds)
            else -> WorkoutState(Phase.WORK, state.set + 1, config.workSeconds)
        }
        return state
    }
}
