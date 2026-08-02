package com.my.parking.domain

import java.time.Instant
import java.time.LocalDate

/** A locally recorded, server-verified reservation submission result. */
data class ReservationHistoryEntry(
    val attemptedAt: Instant,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val maskedVehicleNumber: String,
    val status: ReservationHistoryStatus,
)

enum class ReservationHistoryStatus {
    SUCCESS,
    FAILURE,
}

/** Keeps only the identifying portion needed in the UI; never persist a full vehicle number in history. */
fun VehicleNumber.maskedForHistory(): String =
    value.dropLast(4).ifBlank { "차량" } + "****"
