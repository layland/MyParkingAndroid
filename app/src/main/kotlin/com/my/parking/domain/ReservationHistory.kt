package com.my.parking.domain

import java.time.Instant
import java.time.LocalDate

/** A locally recorded, server-verified reservation submission result. */
data class ReservationHistoryEntry(
    val attemptedAt: Instant,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val vehicleNumber: String,
    val visitorName: String,
    val status: ReservationHistoryStatus,
)

enum class ReservationHistoryStatus {
    SUCCESS,
    FAILURE,
}

/** Entries grouped for a single vehicle's reservation schedule card. */
data class VehicleReservationHistory(
    val vehicleNumber: String,
    val visitorName: String,
    val entries: List<ReservationHistoryEntry>,
)

fun List<ReservationHistoryEntry>.groupByVehicle(): List<VehicleReservationHistory> =
    groupBy(ReservationHistoryEntry::vehicleNumber)
        .map { (vehicleNumber, vehicleEntries) ->
            VehicleReservationHistory(
                vehicleNumber = vehicleNumber,
                visitorName = vehicleEntries.firstNotNullOfOrNull { it.visitorName.takeIf(String::isNotBlank) }
                    .orEmpty(),
                entries = vehicleEntries.sortedByDescending(ReservationHistoryEntry::attemptedAt),
            )
        }
        .sortedByDescending { it.entries.first().attemptedAt }
