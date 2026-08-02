package com.my.parking.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReservationHistoryTest {
    @Test
    fun `masks the final four vehicle characters for history`() {
        val masked = VehicleNumber.of("12가3456").maskedForHistory()

        assertEquals("12가****", masked)
    }

    @Test
    fun `never returns a full short vehicle number`() {
        val masked = VehicleNumber.of("1가2345").maskedForHistory()

        assertEquals("1가****", masked)
    }
}
