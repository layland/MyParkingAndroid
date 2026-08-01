package com.my.parking.domain

import java.time.LocalDate
import org.junit.Assert.assertThrows
import org.junit.Test

class ReservationDateTest {
    @Test
    fun `rejects a past reservation date`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReservationDate.of(LocalDate.now().minusDays(1))
        }
    }
}
