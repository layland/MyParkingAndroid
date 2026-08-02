package com.my.parking.data

/** Allows one submission attempt only; ambiguous outcomes must not be retried automatically. */
class ReservationSubmissionGate {
    var hasStarted: Boolean = false
        private set

    fun begin(): Boolean {
        if (hasStarted) return false
        hasStarted = true
        return true
    }
}
