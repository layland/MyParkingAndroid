package com.my.parking.domain

import java.time.temporal.ChronoUnit

/** A validated inclusive period for one visitor reservation. */
class ReservationPeriod private constructor(
    val startDate: ReservationDate,
    val endDate: ReservationDate,
) {
    val durationDays: Int = (ChronoUnit.DAYS.between(startDate.value, endDate.value) + 1).toInt()

    companion object {
        fun of(startDate: ReservationDate, endDate: ReservationDate): ReservationPeriod {
            require(!endDate.value.isBefore(startDate.value)) {
                "종료일은 시작일보다 빠를 수 없습니다."
            }
            require(ChronoUnit.DAYS.between(startDate.value, endDate.value) < MAX_DURATION_DAYS) {
                "예약 기간은 최대 ${MAX_DURATION_DAYS}일까지 선택할 수 있습니다."
            }
            return ReservationPeriod(startDate, endDate)
        }

        fun of(startDate: ReservationDate, durationDays: Int): ReservationPeriod {
            require(durationDays in 1..MAX_DURATION_DAYS) {
                "예약 기간은 1일부터 ${MAX_DURATION_DAYS}일까지 선택할 수 있습니다."
            }
            return of(startDate, ReservationDate.of(startDate.value.plusDays(durationDays - 1L)))
        }

        const val MAX_DURATION_DAYS = 3
    }
}
