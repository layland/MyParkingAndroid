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
    fun `creates an inclusive seven day period`() {
        val start = ReservationDate.of(LocalDate.now().plusDays(1))
        val period = ReservationPeriod.of(start, ReservationDurationOption.SEVEN_DAYS)

        assertEquals(7, period.durationDays)
        assertEquals(start.value.plusDays(6), period.endDate.value)
    }

    @Test
    fun `calculates a calendar month as the day before the next month date`() {
        val start = ReservationDate.of(LocalDate.now().plusMonths(2))
        val period = ReservationPeriod.of(start, ReservationDurationOption.CALENDAR_MONTH)

        assertEquals(start.value.plusMonths(1).minusDays(1), period.endDate.value)
    }

    @Test
    fun `rejects a period longer than ninety days`() {
        val start = ReservationDate.of(LocalDate.now().plusDays(1))
        assertThrows(IllegalArgumentException::class.java) {
            ReservationPeriod.of(start, ReservationDate.of(start.value.plusDays(90)))
        }
    }

    @Test
    fun `splits a ten day period into four four and two day requests`() {
        val start = ReservationDate.of(LocalDate.now().plusDays(1))
        val periods = ReservationPeriod.splitForSubmission(
            ReservationPeriod.of(start, ReservationDate.of(start.value.plusDays(9))),
        )

        assertEquals(listOf(4, 4, 2), periods.map(ReservationPeriod::durationDays))
        assertEquals(start.value, periods.first().startDate.value)
        assertEquals(start.value.plusDays(9), periods.last().endDate.value)
    }

    @Test
    fun `splits the maximum ninety day period without gaps`() {
        val start = ReservationDate.of(LocalDate.now().plusDays(1))
        val periods = ReservationPeriod.splitForSubmission(
            ReservationPeriod.of(start, ReservationDate.of(start.value.plusDays(89))),
        )

        assertEquals(23, periods.size)
        assertEquals(4, periods.first().durationDays)
        assertEquals(2, periods.last().durationDays)
        assertEquals(start.value.plusDays(89), periods.last().endDate.value)
    }
}
