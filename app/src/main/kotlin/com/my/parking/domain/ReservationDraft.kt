package com.my.parking.domain

/** Domain input for a future reservation request. */
data class ReservationDraft(
    val visitorName: VisitorName,
    val vehicleNumber: VehicleNumber,
    val reservationPeriod: ReservationPeriod,
)
