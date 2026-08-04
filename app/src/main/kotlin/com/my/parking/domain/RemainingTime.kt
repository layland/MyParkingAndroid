package com.my.parking.domain

/** The verified, non-sensitive remaining-time status shown by the official site. */
sealed interface RemainingTimeResult {
    data class Available(val displayText: String) : RemainingTimeResult
    data object LoginRequired : RemainingTimeResult
    data object Unavailable : RemainingTimeResult
    data object NetworkError : RemainingTimeResult
}

object RemainingTimeText {
    fun fromDisplayedText(value: String?): RemainingTimeResult {
        val displayedText = value?.trim().orEmpty()
        return if (displayedText.isNotEmpty()) {
            RemainingTimeResult.Available(displayedText)
        } else {
            RemainingTimeResult.Unavailable
        }
    }
}
