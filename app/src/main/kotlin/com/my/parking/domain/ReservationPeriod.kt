package com.my.parking.domain

import java.time.temporal.ChronoUnit

/** A validated inclusive period for one visitor reservation. */
class ReservationPeriod private constructor(
    val startDate: ReservationDate,
    val endDate: ReservationDate,
) {
    val durationDays: Int = (ChronoUnit.DAYS.between(startDate.value, endDate.value) + 1).toInt()

    companion object {
        const val MAX_DURATION_DAYS = 90
        const val MAX_SUBMISSION_DURATION_DAYS = 4

        fun of(startDate: ReservationDate, endDate: ReservationDate): ReservationPeriod {
            require(!endDate.value.isBefore(startDate.value)) {
                "종료일은 시작일보다 빠를 수 없습니다."
            }
            require(ChronoUnit.DAYS.between(startDate.value, endDate.value) < MAX_DURATION_DAYS) {
                "예약 기간은 최대 ${MAX_DURATION_DAYS}일까지 선택할 수 있습니다."
            }
            return ReservationPeriod(startDate, endDate)
        }

        fun of(startDate: ReservationDate, durationOption: ReservationDurationOption): ReservationPeriod =
            of(startDate, ReservationDate.of(durationOption.endDateFor(startDate.value)))

        /** Splits an inclusive selected period into server-supported requests of at most four days. */
        fun splitForSubmission(period: ReservationPeriod): List<ReservationPeriod> = buildList {
            var nextStart = period.startDate.value
            while (!nextStart.isAfter(period.endDate.value)) {
                val nextEnd = minOf(
                    nextStart.plusDays(MAX_SUBMISSION_DURATION_DAYS - 1L),
                    period.endDate.value,
                )
                add(of(ReservationDate.of(nextStart), ReservationDate.of(nextEnd)))
                nextStart = nextEnd.plusDays(1)
            }
        }
    }
}
