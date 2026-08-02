package com.my.parking.domain

/** Domain input for a future reservation request. */
data class ReservationDraft(
    val visitorName: VisitorName,
    val vehicleNumber: VehicleNumber,
    /** Non-null only when the user chose one of the quick duration presets. */
    val durationOption: ReservationDurationOption?,
    val reservationPeriod: ReservationPeriod,
)
