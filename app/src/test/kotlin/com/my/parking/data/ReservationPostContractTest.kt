package com.my.parking.data

import com.my.parking.domain.ReservationDate
import com.my.parking.domain.ReservationDraft
import com.my.parking.domain.ReservationDurationOption
import com.my.parking.domain.ReservationPeriod
import com.my.parking.domain.VehicleNumber
import com.my.parking.domain.VisitorName
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReservationPostContractTest {
    @Test
    fun `builds the observed request parameters from the reservation draft`() {
        val draft = ReservationDraft(
            visitorName = VisitorName.of("홍길동"),
            vehicleNumber = VehicleNumber.of("12가3456"),
            durationOption = ReservationDurationOption.THREE_DAYS,
            reservationPeriod = ReservationPeriod.of(
                ReservationDate.of(LocalDate.now().plusDays(1)),
                ReservationDurationOption.THREE_DAYS,
            ),
        )

        val parameters = ReservationPostContract.parameters(draft)

        assertEquals("12가3456", parameters["carNo"])
        assertEquals("홍길동", parameters["name"])
        assertEquals(draft.reservationPeriod.startDate.value.toString(), parameters["bookStartDate"])
        assertEquals(draft.reservationPeriod.endDate.value.toString(), parameters["bookEndDate"])
        assertEquals("1", parameters["parkingLot"])
        assertEquals("래미안영통마크원2단지", parameters["siteName"])
        assertEquals("210", parameters["compName"])
        assertEquals("504", parameters["deptName"])
    }

    @Test
    fun `uses the supplied split period for request dates`() {
        val start = ReservationDate.of(LocalDate.now().plusDays(1))
        val draft = ReservationDraft(
            visitorName = VisitorName.of("홍길동"),
            vehicleNumber = VehicleNumber.of("12가3456"),
            durationOption = null,
            reservationPeriod = ReservationPeriod.of(start, ReservationDate.of(start.value.plusDays(4))),
        )
        val firstPeriod = ReservationPeriod.of(start, ReservationDate.of(start.value.plusDays(3)))

        val parameters = ReservationPostContract.parameters(draft, firstPeriod)

        assertEquals(start.value.toString(), parameters["bookStartDate"])
        assertEquals(start.value.plusDays(3).toString(), parameters["bookEndDate"])
    }
}
