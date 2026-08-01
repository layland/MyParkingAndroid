package com.my.parking.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleNumberTest {
    @Test
    fun `removes whitespace and masks vehicle number`() {
        val vehicleNumber = VehicleNumber.of("12가 3456")

        assertEquals("12가3456", vehicleNumber.value)
        assertEquals("12가****", vehicleNumber.masked())
    }
}
