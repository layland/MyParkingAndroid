package com.my.parking.data

import com.my.parking.domain.ReservationDraft
import com.my.parking.domain.ReservationPeriod

/** Observed request contract for the resident's official visitor-parking service. */
object ReservationPostContract {
    fun parameters(
        draft: ReservationDraft,
        reservationPeriod: ReservationPeriod = draft.reservationPeriod,
    ): LinkedHashMap<String, String> = linkedMapOf(
        "id" to "",
        "siteName" to "\uB798\uBBF8\uC548\uC601\uD1B5\uB9C8\uD06C\uC6D02\uB2E8\uC9C0",
        "selectParkingZone" to "true",
        "parkingLot" to "1",
        "parkingZone" to "1",
        "name" to draft.visitorName.value,
        "carNo" to draft.vehicleNumber.value,
        "tel" to "",
        "compName" to "210",
        "deptName" to "504",
        "address" to "",
        "bookStartDate" to reservationPeriod.startDate.value.toString(),
        "bookEndDate" to reservationPeriod.endDate.value.toString(),
    )
}
