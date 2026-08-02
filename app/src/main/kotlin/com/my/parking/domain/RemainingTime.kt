package com.my.parking.domain

/** The verified, non-sensitive remaining-time status shown by the official site. */
sealed interface RemainingTimeResult {
    data class Available(val displayText: String) : RemainingTimeResult
    data object LoginRequired : RemainingTimeResult
    data object Unavailable : RemainingTimeResult
    data object NetworkError : RemainingTimeResult
}

object RemainingTimeText {
    private val displayPattern = Regex("^\\d{1,4}\\uC2DC\\uAC04 \\d{1,2}\\uBD84 \\uB0A8\\uC74C$")

    fun fromDisplayedText(value: String?): RemainingTimeResult {
        val normalized = value?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        return if (displayPattern.matches(normalized)) {
            RemainingTimeResult.Available(normalized)
        } else {
            RemainingTimeResult.Unavailable
        }
    }
}
