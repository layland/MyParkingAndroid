package com.my.parking.domain

/** Small, purpose-specific interface for the user's default vehicle. */
interface VehiclePreferences {
    fun defaultVehicleNumber(): String?

    fun vehicleNumbers(): List<String>

    fun saveDefaultVehicleNumber(vehicleNumber: VehicleNumber)

    fun saveReservationDraft(draft: ReservationDraft)

    fun visitorNameFor(vehicleNumber: VehicleNumber): VisitorName?

    fun reservationDurationOptionFor(vehicleNumber: VehicleNumber): ReservationDurationOption?

    fun removeVehicleNumber(vehicleNumber: VehicleNumber)
}
