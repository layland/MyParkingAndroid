package com.my.parking.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class VisitorNameTest {
    @Test
    fun `accepts an omitted name`() {
        assertEquals("", VisitorName.of(null).value)
    }

    @Test
    fun `trims an optional name`() {
        assertEquals("홍길동", VisitorName.of("  홍길동  ").value)
    }
}
