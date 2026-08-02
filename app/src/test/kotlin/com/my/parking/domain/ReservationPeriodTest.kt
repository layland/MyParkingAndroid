package com.my.parking.domain

import java.time.LocalDate
import org.junit.Assert.assertThrows
import org.junit.Test

class ReservationPeriodTest {
    @Test
    fun `rejects an end date before the start date`() {
        val start = ReservationDate.of(LocalDate.now().plusDays(2))
        val end = ReservationDate.of(LocalDate.now().plusDays(1))

        assertThrows(IllegalArgumentException::class.java) {
            ReservationPeriod.of(start, end)
        }
    }
}
