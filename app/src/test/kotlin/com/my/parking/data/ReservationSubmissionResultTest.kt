package com.my.parking.data

import org.junit.Assert.assertTrue
import org.junit.Test

class ReservationSubmissionResultTest {
    @Test
    fun `accepts only a successful HTTP response with the observed success result`() {
        val result = ReservationSubmissionResult.from(200, "success", "성공적으로 등록되었습니다.")

        assertTrue(result is ReservationSubmissionResult.Success)
    }

    @Test
    fun `does not treat server errors or unknown responses as success`() {
        assertTrue(ReservationSubmissionResult.from(500, "success", null) is ReservationSubmissionResult.Failure)
        assertTrue(ReservationSubmissionResult.from(200, "error", null) is ReservationSubmissionResult.Failure)
        assertTrue(ReservationSubmissionResult.from(0, null, null) is ReservationSubmissionResult.Failure)
    }
}
