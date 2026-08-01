package com.my.parking.domain

/** Small, purpose-specific interface for the user's default vehicle. */
interface VehiclePreferences {
    fun defaultVehicleNumber(): String?

    fun vehicleNumbers(): List<String>

    fun saveDefaultVehicleNumber(vehicleNumber: VehicleNumber)

    fun removeVehicleNumber(vehicleNumber: VehicleNumber)
}
