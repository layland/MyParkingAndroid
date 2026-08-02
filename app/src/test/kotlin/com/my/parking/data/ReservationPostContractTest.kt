package com.my.parking.data

import com.my.parking.domain.ReservationDate
import com.my.parking.domain.ReservationDraft
import com.my.parking.domain.ReservationPeriod
import com.my.parking.domain.VehicleNumber
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReservationPostContractTest {
    @Test
    fun `builds the observed request parameters from the reservation draft`() {
        val draft = ReservationDraft(
            vehicleNumber = VehicleNumber.of("12가3456"),
            reservationPeriod = ReservationPeriod.of(
                ReservationDate.of(LocalDate.now().plusDays(1)),
                ReservationDate.of(LocalDate.now().plusDays(4)),
            ),
        )

        val parameters = ReservationPostContract.parameters(draft)

        assertEquals("12가3456", parameters["carNo"])
        assertEquals(draft.reservationPeriod.startDate.value.toString(), parameters["bookStartDate"])
        assertEquals(draft.reservationPeriod.endDate.value.toString(), parameters["bookEndDate"])
        assertEquals("1", parameters["parkingLot"])
        assertEquals("래미안영통마크원2단지", parameters["siteName"])
        assertEquals("210", parameters["compName"])
        assertEquals("504", parameters["deptName"])
    }
}
