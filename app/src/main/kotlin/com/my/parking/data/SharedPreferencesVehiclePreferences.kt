package com.my.parking.data

import android.content.Context
import com.my.parking.domain.ReservationDraft
import com.my.parking.domain.ReservationDurationOption
import com.my.parking.domain.VehicleNumber
import com.my.parking.domain.VehiclePreferences
import com.my.parking.domain.VisitorName
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Stores only the non-secret default vehicle number. Authentication is not stored here. */
class SharedPreferencesVehiclePreferences(context: Context) : VehiclePreferences {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun defaultVehicleNumber(): String? = preferences.getString(KEY_DEFAULT_VEHICLE, null)

    override fun vehicleNumbers(): List<String> {
        val numbers = preferences.getStringSet(KEY_VEHICLE_NUMBERS, emptySet()).orEmpty().toMutableSet()
        defaultVehicleNumber()?.let(numbers::add)
        val default = defaultVehicleNumber()
        return numbers.sorted().let { savedNumbers ->
            if (default != null && default in savedNumbers) listOf(default) + savedNumbers.filterNot { it == default }
            else savedNumbers
        }
    }

    override fun saveDefaultVehicleNumber(vehicleNumber: VehicleNumber) {
        val numbers = vehicleNumbers().toMutableSet().apply { add(vehicleNumber.value) }
        preferences.edit()
            .putStringSet(KEY_VEHICLE_NUMBERS, numbers)
            .putString(KEY_DEFAULT_VEHICLE, vehicleNumber.value)
            .apply()
    }

    override fun saveReservationDraft(draft: ReservationDraft) {
        val numbers = vehicleNumbers().toMutableSet().apply { add(draft.vehicleNumber.value) }
        preferences.edit()
            .putStringSet(KEY_VEHICLE_NUMBERS, numbers)
            .putString(KEY_DEFAULT_VEHICLE, draft.vehicleNumber.value)
            .putString(nameKey(draft.vehicleNumber), draft.visitorName.value)
            .putString(
                periodKey(draft.vehicleNumber),
                draft.durationOption?.storageValue
                    ?: "${draft.reservationPeriod.startDate.value}|${draft.reservationPeriod.endDate.value}",
            )
            .apply()
    }

    override fun visitorNameFor(vehicleNumber: VehicleNumber): VisitorName? =
        preferences.getString(nameKey(vehicleNumber), null)?.let { rawName ->
            runCatching { VisitorName.of(rawName) }.getOrNull()
        }

    override fun reservationDurationOptionFor(vehicleNumber: VehicleNumber): ReservationDurationOption? {
        val storedPeriod = preferences.getString(periodKey(vehicleNumber), null) ?: return null
        ReservationDurationOption.fromStorageValue(storedPeriod)?.let { return it }
        val dates = storedPeriod.split('|')
        if (dates.size != 2) return null
        return runCatching {
            (ChronoUnit.DAYS.between(LocalDate.parse(dates[0]), LocalDate.parse(dates[1])) + 1).toInt()
        }.getOrNull()?.toString()?.let(ReservationDurationOption::fromStorageValue)
    }

    override fun removeVehicleNumber(vehicleNumber: VehicleNumber) {
        val numbers = vehicleNumbers().toMutableSet().apply { remove(vehicleNumber.value) }
        val nextDefault = defaultVehicleNumber()?.takeUnless { it == vehicleNumber.value }
            ?: numbers.sorted().firstOrNull()
        preferences.edit()
            .putStringSet(KEY_VEHICLE_NUMBERS, numbers)
            .apply {
                if (nextDefault == null) remove(KEY_DEFAULT_VEHICLE)
                else putString(KEY_DEFAULT_VEHICLE, nextDefault)
                remove(periodKey(vehicleNumber))
                remove(nameKey(vehicleNumber))
            }
            .apply()
    }

    private fun periodKey(vehicleNumber: VehicleNumber): String = "$KEY_PERIOD_PREFIX${vehicleNumber.value}"

    private fun nameKey(vehicleNumber: VehicleNumber): String = "$KEY_NAME_PREFIX${vehicleNumber.value}"

    private companion object {
        const val PREFERENCES_NAME = "vehicle_preferences"
        const val KEY_DEFAULT_VEHICLE = "default_vehicle"
        const val KEY_VEHICLE_NUMBERS = "vehicle_numbers"
        const val KEY_PERIOD_PREFIX = "reservation_period_"
        const val KEY_NAME_PREFIX = "visitor_name_"
    }
}
