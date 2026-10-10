package com.layland.myexercise

import org.junit.Assert.*
import org.junit.Test

class BreathingTest {
    private fun config(sets: Int = 1) = WorkoutConfig(
        workSeconds = 5, restSeconds = 10, sets = sets,
        startPhrase = "들이마시세요", restPhrase = "내쉬세요",
        finishPhrase = "완료", encouragePhrase = "응원", mode = ExerciseMode.BREATHING,
    )
    private fun tickWithCues(engine: WorkoutEngine): List<VoiceCue> {
        val before = engine.state
        return voiceCues(engine.config, before, engine.tick())
    }
    @Test fun fiveSecondInhaleThenTenSecondExhaleIncludesFinalCounts() {
        val engine = WorkoutEngine(config())
        engine.start()
        (1..4).forEach { assertEquals(listOf(VoiceCue.Count(it)), tickWithCues(engine)) }
        assertEquals(listOf(VoiceCue.Count(5), VoiceCue.Phrase("내쉬세요")), tickWithCues(engine))
        assertEquals(Phase.REST, engine.state.phase)
        (1..9).forEach { assertEquals(listOf(VoiceCue.Count(it)), tickWithCues(engine)) }
        assertEquals(listOf(VoiceCue.Count(10), VoiceCue.Phrase("완료")), tickWithCues(engine))
        assertEquals(Phase.DONE, engine.state.phase)
        assertTrue(tickWithCues(engine).isEmpty())
    }
    @Test fun eachCycleResetsCountAndEndsAfterFinalExhale() {
        val engine = WorkoutEngine(config(sets = 2))
        engine.start()
        repeat(14) { tickWithCues(engine) }
        assertEquals(listOf(VoiceCue.Count(10), VoiceCue.Phrase("들이마시세요")), tickWithCues(engine))
        assertEquals(WorkoutState(Phase.WORK, 2, 5), engine.state)
        assertEquals(listOf(VoiceCue.Count(1)), tickWithCues(engine))
        repeat(13) { tickWithCues(engine) }
        assertEquals(Phase.REST, engine.state.phase)
        assertEquals(listOf(VoiceCue.Count(10), VoiceCue.Phrase("완료")), tickWithCues(engine))
    }
    @Test fun pauseIsSilentAndResumeContinuesCount() {
        val engine = WorkoutEngine(config())
        engine.start(); tickWithCues(engine); engine.pause()
        assertTrue(tickWithCues(engine).isEmpty())
        assertEquals(4, engine.state.remaining)
        engine.resume()
        assertEquals(listOf(VoiceCue.Count(2)), tickWithCues(engine))
    }
    @Test fun customDurationsAndOneSecondPhasesWork() {
        val engine = WorkoutEngine(config().copy(workSeconds = 1, restSeconds = 1))
        engine.start()
        assertEquals(listOf(VoiceCue.Count(1), VoiceCue.Phrase("내쉬세요")), tickWithCues(engine))
        assertEquals(listOf(VoiceCue.Count(1), VoiceCue.Phrase("완료")), tickWithCues(engine))
    }
    @Test fun breathingRequiresAnExhaleButStandardMaySkipRest() {
        assertFalse(config().copy(restSeconds = 0).valid())
        assertTrue(config().copy(restSeconds = 0, mode = ExerciseMode.STANDARD).valid())
    }
    @Test fun standardStillUsesRemainingTimeAndEncouragement() {
        val engine = WorkoutEngine(config().copy(mode = ExerciseMode.STANDARD, countdownSeconds = 2, encourageEvery = 1))
        engine.start()
        assertEquals(listOf(VoiceCue.Phrase("응원")), tickWithCues(engine))
        tickWithCues(engine)
        assertEquals(listOf(VoiceCue.Remaining(2)), tickWithCues(engine))
        assertEquals(listOf(VoiceCue.Remaining(1)), tickWithCues(engine))
        assertEquals(listOf(VoiceCue.Phrase("완료")), tickWithCues(engine))
    }
    @Test fun nativeKoreanCountsSupportCustomDurationsAndBoundaryValues() {
        val words = KoreanCountWords(
            listOf("", "하나", "둘", "셋", "넷", "다섯", "여섯", "일곱", "여덟", "아홉"),
            listOf("", "열", "스물", "서른", "마흔", "쉰", "예순", "일흔", "여든", "아흔"),
            listOf("", "", "이", "삼", "사", "오", "육", "칠", "팔", "구"), "백", "천",
        )
        assertEquals("하나", words.format(1))
        assertEquals("다섯", words.format(5))
        assertEquals("열", words.format(10))
        assertEquals("열다섯", words.format(15))
        assertEquals("스물둘", words.format(22))
        assertEquals("백", words.format(100))
        assertEquals("백하나", words.format(101))
        assertEquals("삼천육백", words.format(3600))
    }
}
