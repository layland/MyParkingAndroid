package com.my.parking.data

import com.my.parking.domain.ReservationHistoryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ReservationHistoryStatusMapperTest {
    @Test
    fun `records a verified success as success`() {
        assertEquals(
            ReservationHistoryStatus.SUCCESS,
            ReservationSubmissionResult.Success("confirmed").historyStatus(),
        )
    }

    @Test
    fun `records server failures, unknown results, and partial batches as failures`() {
        assertEquals(
            ReservationHistoryStatus.FAILURE,
            ReservationSubmissionResult.Failure("server failure").historyStatus(),
        )
        assertEquals(
            ReservationHistoryStatus.FAILURE,
            ReservationSubmissionResult.Failure("result unknown").historyStatus(),
        )
        assertEquals(
            ReservationHistoryStatus.FAILURE,
            ReservationSubmissionResult.Failure("1 of 2 reservations were submitted").historyStatus(),
        )
    }
}
