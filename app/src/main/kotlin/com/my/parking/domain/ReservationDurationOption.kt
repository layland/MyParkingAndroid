package com.my.parking.domain

import java.time.LocalDate

/** The fixed reservation-duration choices supported by the app. */
enum class ReservationDurationOption(
    val storageValue: String,
    val displayText: String,
) {
    ONE_DAY("1", "1일"),
    TWO_DAYS("2", "2일"),
    THREE_DAYS("3", "3일"),
    FOUR_DAYS("4", "4일"),
    SEVEN_DAYS("7", "7일"),
    CALENDAR_MONTH("calendar_month", "한 달");

    fun endDateFor(startDate: LocalDate): LocalDate = when (this) {
        CALENDAR_MONTH -> startDate.plusMonths(1).minusDays(1)
        else -> startDate.plusDays(storageValue.toLong() - 1)
    }

    companion object {
        fun fromStorageValue(value: String?): ReservationDurationOption? =
            entries.firstOrNull { it.storageValue == value }
    }
}
