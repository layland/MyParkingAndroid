package com.my.parking.domain

/** Normalizes vehicle numbers before the server-specific validation is known. */
@JvmInline
value class VehicleNumber private constructor(val value: String) {
    fun masked(): String = if (value.length <= 4) "****" else value.take(3) + "****"

    companion object {
        fun of(rawValue: String?): VehicleNumber {
            val normalized = rawValue.orEmpty().replace(Regex("\\s"), "")
            require(normalized.isNotEmpty()) { "차량번호를 입력해 주세요." }
            require(normalized.length <= 12) { "차량번호를 다시 확인해 주세요." }
            return VehicleNumber(normalized)
        }
    }
}
