package com.my.parking.domain

/** An optional visitor name supplied with a saved vehicle and reservation request. */
@JvmInline
value class VisitorName private constructor(val value: String) {
    companion object {
        fun of(rawValue: String?): VisitorName {
            val normalized = rawValue.orEmpty().trim()
            require(normalized.length <= 40) { "이름을 다시 확인해 주세요." }
            return VisitorName(normalized)
        }
    }
}
