package com.my.parking.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservationSubmissionGateTest {
    @Test
    fun `allows only one submission attempt`() {
        val gate = ReservationSubmissionGate()

        assertTrue(gate.begin())
        assertFalse(gate.begin())
    }
}
