package com.my.parking.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RemainingTimeTextTest {
    @Test
    fun `preserves the official remaining-time text`() {
        assertEquals(
            RemainingTimeResult.Available("104\uC2DC 55\uBD84 \uB0A8\uC74C"),
            RemainingTimeText.fromDisplayedText(" 104\uC2DC 55\uBD84 \uB0A8\uC74C "),
        )
    }

    @Test
    fun `rejects a blank remaining-time text`() {
        assertEquals(RemainingTimeResult.Unavailable, RemainingTimeText.fromDisplayedText("   "))
    }
}
