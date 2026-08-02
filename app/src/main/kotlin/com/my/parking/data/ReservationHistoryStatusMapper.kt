package com.my.parking.data

import com.my.parking.domain.ReservationHistoryStatus

/** Converts every non-success submission outcome, including partial batches, to a failed history entry. */
fun ReservationSubmissionResult.historyStatus(): ReservationHistoryStatus = when (this) {
    is ReservationSubmissionResult.Success -> ReservationHistoryStatus.SUCCESS
    is ReservationSubmissionResult.Failure -> ReservationHistoryStatus.FAILURE
}
