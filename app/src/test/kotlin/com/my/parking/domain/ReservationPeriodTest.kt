package com.my.parking.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
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

    @Test
    fun `creates an inclusive period of up to three days`() {
        val start = ReservationDate.of(LocalDate.now().plusDays(1))
        val period = ReservationPeriod.of(start, 3)

        assertEquals(3, period.durationDays)
        assertEquals(start.value.plusDays(2), period.endDate.value)
    }

    @Test
    fun `rejects a period longer than three days`() {
        val start = ReservationDate.of(LocalDate.now().plusDays(1))

        assertThrows(IllegalArgumentException::class.java) {
            ReservationPeriod.of(start, 4)
        }
    }
}
