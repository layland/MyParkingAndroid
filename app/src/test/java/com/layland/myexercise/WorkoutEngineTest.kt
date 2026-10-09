package com.layland.myexercise

import org.junit.Assert.*
import org.junit.Test

class WorkoutEngineTest {
    private fun config(work: Int = 2, rest: Int = 1, sets: Int = 2) = WorkoutConfig(
        workSeconds = work, restSeconds = rest, sets = sets,
        startPhrase = "", restPhrase = "", finishPhrase = "", encouragePhrase = ""
    )
    @Test fun completesAllSetsWithoutTrailingRest() {
        val engine = WorkoutEngine(config())
        engine.start()
        assertEquals(1, engine.tick().remaining)
        assertEquals(Phase.REST, engine.tick().phase)
        assertEquals(2, engine.tick().set)
        engine.tick()
        assertEquals(Phase.DONE, engine.tick().phase)
        assertEquals(0, engine.state.remaining)
    }
    @Test fun pausePreservesTimeAndResumeContinues() {
        val engine = WorkoutEngine(config())
        engine.start(); engine.pause()
        repeat(10) { engine.tick() }
        assertEquals(2, engine.state.remaining)
        engine.resume()
        assertEquals(1, engine.tick().remaining)
    }
    @Test fun zeroRestMovesDirectlyToNextSet() {
        val engine = WorkoutEngine(config(work = 1, rest = 0))
        engine.start()
        assertEquals(WorkoutState(Phase.WORK, 2, 1), engine.tick())
        assertEquals(Phase.DONE, engine.tick().phase)
        assertEquals(Phase.DONE, engine.tick().phase)
    }
    @Test fun resetAllowsFreshSession() {
        val engine = WorkoutEngine(config())
        engine.start(); engine.tick(); engine.reset()
        assertEquals(WorkoutState(), engine.state)
        assertEquals(2, engine.start().remaining)
    }
    @Test fun invalidLimitsAreRejected() {
        assertFalse(config(work = 0).valid())
        assertFalse(config(rest = -1).valid())
        assertFalse(config(sets = 101).valid())
        assertFalse(config().copy(countdownSeconds = 61).valid())
        assertFalse(config().copy(encourageEvery = -1).valid())
    }
}
