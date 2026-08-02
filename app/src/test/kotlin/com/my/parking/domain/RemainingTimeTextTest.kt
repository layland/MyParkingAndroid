package com.my.parking.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RemainingTimeTextTest {
    @Test
    fun `accepts the verified remaining time label`() {
        assertEquals(
            RemainingTimeResult.Available("111시간 16분 남음"),
            RemainingTimeText.fromDisplayedText(" 111시간   16분 남음 "),
        )
    }

    @Test
    fun `rejects unrelated page text`() {
        assertEquals(RemainingTimeResult.Unavailable, RemainingTimeText.fromDisplayedText("예약 목록"))
    }
}
