package com.my.parking.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A validated reservation date that the UI can display without knowing server formats. */
@JvmInline
value class ReservationDate private constructor(val value: LocalDate) {
    fun displayText(): String = value.format(DISPLAY_FORMAT)

    companion object {
        private val DISPLAY_FORMAT = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

        fun of(value: LocalDate): ReservationDate {
            require(!value.isBefore(LocalDate.now())) { "지난 날짜에는 예약할 수 없어요." }
            return ReservationDate(value)
        }

        fun today(): ReservationDate = of(LocalDate.now())
    }
}
