package com.my.parking.domain

/** A validated inclusive period for one visitor reservation. */
class ReservationPeriod private constructor(
    val startDate: ReservationDate,
    val endDate: ReservationDate,
) {
    companion object {
        fun of(startDate: ReservationDate, endDate: ReservationDate): ReservationPeriod {
            require(!endDate.value.isBefore(startDate.value)) {
                "종료일은 시작일보다 빠를 수 없습니다."
            }
            return ReservationPeriod(startDate, endDate)
        }
    }
}
