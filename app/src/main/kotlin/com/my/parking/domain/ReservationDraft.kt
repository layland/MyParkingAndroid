package com.my.parking.domain

/** Domain input for a future reservation request. */
data class ReservationDraft(
    val vehicleNumber: VehicleNumber,
    val reservationPeriod: ReservationPeriod,
)
