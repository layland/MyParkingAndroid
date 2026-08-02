package com.my.parking.domain

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReservationHistoryTest {
    @Test
    fun `groups reservation schedules by vehicle and keeps latest vehicle first`() {
        val firstVehicle = "12가3456"
        val secondVehicle = "34나5678"
        val histories = listOf(
            history(firstVehicle, "김방문", "2026-08-02T08:00:00Z"),
            history(secondVehicle, "이방문", "2026-08-02T10:00:00Z"),
            history(firstVehicle, "김방문", "2026-08-02T09:00:00Z"),
        ).groupByVehicle()

        assertEquals(listOf(secondVehicle, firstVehicle), histories.map { it.vehicleNumber })
        assertEquals("김방문", histories[1].visitorName)
        assertEquals(2, histories[1].entries.size)
        assertEquals(Instant.parse("2026-08-02T09:00:00Z"), histories[1].entries.first().attemptedAt)
    }

    private fun history(vehicleNumber: String, visitorName: String, attemptedAt: String) = ReservationHistoryEntry(
        attemptedAt = Instant.parse(attemptedAt),
        startDate = LocalDate.parse("2026-08-03"),
        endDate = LocalDate.parse("2026-08-04"),
        vehicleNumber = vehicleNumber,
        visitorName = visitorName,
        status = ReservationHistoryStatus.SUCCESS,
    )
}
