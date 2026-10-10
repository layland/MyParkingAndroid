package com.layland.myexercise

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BreathingSessionTest {
    private fun engine(work: Int = 5, rest: Int = 10, sets: Int = 1) = WorkoutEngine(WorkoutConfig(
        workSeconds = work, restSeconds = rest, sets = sets,
        startPhrase = "inhale", restPhrase = "exhale", finishPhrase = "finish", encouragePhrase = "",
        mode = ExerciseMode.BREATHING,
    )).also { it.start() }
    private class FakeSpeech : SpeechCoach {
        data class Request(val text: String, val done: CompletableDeferred<Boolean> = CompletableDeferred())
        val requests = mutableListOf<Request>()
        override fun speak(text: String, enqueue: Boolean) { error("Breathing must await playback") }
        override suspend fun playAndWait(text: String): Boolean = Request(text).also { requests.add(it) }.done.await()
        fun finishLatest() { requests.last().done.complete(true) }
        override fun stop() { requests.forEach { it.done.complete(false) } }
        override fun close() = stop()
    }
    private fun TestScope.session(engine: WorkoutEngine, speech: SpeechCoach, round: (Int) -> String = { "" }) = BreathingSession(
        engine, speech, { it.toString() }, { testScheduler.currentTime },
        { deadline -> delay((deadline - testScheduler.currentTime).coerceAtLeast(0L)) }, {}, round,
    )
    @Test fun timeAndCountsWaitForActualInstructionCompletion() = runTest {
        val engine = engine(); val speech = FakeSpeech(); val session = session(engine, speech)
        val job = launch { session.run() }
        runCurrent()
        advanceTimeBy(5000); runCurrent()
        assertEquals(5, engine.state.remaining)
        assertEquals(listOf("inhale"), speech.requests.map { it.text })
        speech.finishLatest(); runCurrent()
        advanceTimeBy(999); runCurrent()
        assertEquals(5, engine.state.remaining)
        advanceTimeBy(1); runCurrent()
        assertEquals(4, engine.state.remaining)
        assertEquals(listOf("inhale", "1"), speech.requests.map { it.text })
        job.cancelAndJoin(); speech.stop()
    }
    @Test fun finalNumberFinishesBeforeNextInstructionAndCompletion() = runTest {
        val engine = engine(work = 1, rest = 1); val speech = FakeSpeech()
        val session = session(engine, speech); val job = launch { session.run() }
        runCurrent(); speech.finishLatest(); runCurrent()
        advanceTimeBy(1000); runCurrent()
        assertEquals(listOf("inhale", "1"), speech.requests.map { it.text })
        speech.finishLatest(); runCurrent()
        assertEquals(listOf("inhale", "1", "exhale"), speech.requests.map { it.text })
        advanceTimeBy(5000); runCurrent()
        assertEquals(1, engine.state.remaining)
        speech.finishLatest(); runCurrent()
        advanceTimeBy(1000); runCurrent()
        assertEquals(listOf("inhale", "1", "exhale", "1"), speech.requests.map { it.text })
        speech.finishLatest(); runCurrent()
        assertEquals("finish", speech.requests.last().text)
        assertFalse(job.isCompleted)
        speech.finishLatest(); runCurrent(); job.join()
        assertEquals(Phase.DONE, engine.state.phase)
    }
    @Test fun pauseDuringInstructionCancelsWaitAndResumeReplaysInstruction() = runTest {
        val engine = engine(); val speech = FakeSpeech(); val session = session(engine, speech)
        val job = launch { session.run() }; runCurrent()
        engine.pause(); job.cancelAndJoin(); speech.stop()
        advanceTimeBy(5000); runCurrent()
        assertEquals(5, engine.state.remaining)
        engine.resume()
        val resumed = launch { session.run() }; runCurrent()
        assertEquals(listOf("inhale", "inhale"), speech.requests.map { it.text })
        speech.finishLatest(); runCurrent()
        advanceTimeBy(1000); runCurrent()
        assertEquals("1", speech.requests.last().text)
        resumed.cancelAndJoin(); speech.stop()
    }
    @Test fun pauseAfterFirstCountResumesAtSecondWithoutRepeatingInstruction() = runTest {
        val engine = engine(); val speech = FakeSpeech(); val session = session(engine, speech)
        val job = launch { session.run() }; runCurrent()
        speech.finishLatest(); runCurrent(); advanceTimeBy(1000); runCurrent()
        engine.pause(); job.cancelAndJoin(); speech.stop()
        engine.resume()
        val resumed = launch { session.run() }; runCurrent()
        advanceTimeBy(1000); runCurrent()
        assertEquals(listOf("inhale", "1", "2"), speech.requests.map { it.text })
        assertEquals(3, engine.state.remaining)
        resumed.cancelAndJoin(); speech.stop()
    }
    @Test fun eachRoundIsAnnouncedOnceBeforeInhaleAndTimer() = runTest {
        val engine = engine(work = 1, rest = 1, sets = 2)
        val speech = FakeSpeech()
        val job = launch { session(engine, speech) { "round $it" }.run() }
        runCurrent()
        assertEquals(listOf("round 1"), speech.requests.map { it.text })
        advanceTimeBy(5000); runCurrent()
        assertEquals(1, engine.state.remaining)
        speech.finishLatest(); runCurrent()
        assertEquals("inhale", speech.requests.last().text)
        advanceTimeBy(5000); runCurrent()
        assertEquals(1, engine.state.remaining)
        speech.finishLatest(); runCurrent()
        advanceTimeBy(1000); runCurrent()
        speech.finishLatest(); runCurrent() // Final inhale number -> exhale, without round announcement.
        speech.finishLatest(); runCurrent()
        advanceTimeBy(1000); runCurrent()
        assertEquals(listOf("round 1", "inhale", "1", "exhale", "1"), speech.requests.map { it.text })
        speech.finishLatest(); runCurrent()
        assertEquals("round 2", speech.requests.last().text)
        advanceTimeBy(5000); runCurrent()
        assertEquals(1, engine.state.remaining)
        speech.finishLatest(); runCurrent()
        assertEquals("inhale", speech.requests.last().text)
        job.cancelAndJoin(); speech.stop()
    }
    @Test fun unavailableSpeechDoesNotHangTheTimer() = runTest {
        val speech = object : SpeechCoach {
            override fun speak(text: String, enqueue: Boolean) {}
            override suspend fun playAndWait(text: String) = false
            override fun stop() {}
            override fun close() {}
        }
        val engine = engine(work = 1, rest = 1)
        val job = launch { session(engine, speech).run() }
        advanceUntilIdle(); job.join()
        assertEquals(Phase.DONE, engine.state.phase)
        assertEquals(2000L, testScheduler.currentTime)
    }
}
