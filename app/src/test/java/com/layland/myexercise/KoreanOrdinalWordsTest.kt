package com.layland.myexercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreanOrdinalWordsTest {
    private val words = KoreanOrdinalWords("첫",
        listOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉"),
        listOf("", "열", "스물", "서른", "마흔", "쉰", "예순", "일흔", "여든", "아흔"), "스무", "백")
    @Test fun usesOrdinalFormsAcrossTheSupportedSetRange() {
        val cases = mapOf(1 to "첫", 2 to "두", 3 to "세", 4 to "네", 10 to "열", 11 to "열한",
            20 to "스무", 21 to "스물한", 30 to "서른", 99 to "아흔아홉", 100 to "백")
        cases.forEach { (number, expected) -> assertEquals(expected, words.format(number)) }
        (1..WorkoutConfig.MAX_SETS).forEach { number -> assertTrue(words.format(number).isNotBlank()) }
    }
}
