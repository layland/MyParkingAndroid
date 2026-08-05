package com.my.parking.domain

/** Owns the local record of verified reservation-submission outcomes. */
interface ReservationHistoryRepository {
    fun entries(): List<ReservationHistoryEntry>

    fun record(draft: ReservationDraft, status: ReservationHistoryStatus)

    fun removeVehicleHistory(vehicleNumber: String)

    fun clear()
}
