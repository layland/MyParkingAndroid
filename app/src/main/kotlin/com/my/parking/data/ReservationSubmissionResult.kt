package com.my.parking.data

/** Server response after a single, non-retried visitor-reservation submission. */
sealed interface ReservationSubmissionResult {
    data class Success(val message: String) : ReservationSubmissionResult
    data class Failure(val message: String) : ReservationSubmissionResult

    companion object {
        fun from(httpStatus: Int, result: String?, message: String?): ReservationSubmissionResult =
            if (httpStatus in 200..299 && result == "success") {
                Success(message.orEmpty())
            } else {
                Failure(message?.takeIf(String::isNotBlank) ?: "등록 결과를 확인할 수 없습니다.")
            }
    }
}
