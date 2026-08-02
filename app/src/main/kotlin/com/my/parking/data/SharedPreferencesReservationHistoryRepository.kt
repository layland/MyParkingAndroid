package com.my.parking.data

import android.content.Context
import com.my.parking.domain.ReservationDraft
import com.my.parking.domain.ReservationHistoryEntry
import com.my.parking.domain.ReservationHistoryRepository
import com.my.parking.domain.ReservationHistoryStatus
import com.my.parking.domain.maskedForHistory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/** Separate local storage for submission history; it intentionally never contains full vehicle numbers. */
class SharedPreferencesReservationHistoryRepository(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ReservationHistoryRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun entries(): List<ReservationHistoryEntry> = readEntries()
        .sortedByDescending(ReservationHistoryEntry::attemptedAt)

    override fun record(draft: ReservationDraft, status: ReservationHistoryStatus) {
        val entry = ReservationHistoryEntry(
            attemptedAt = clock.instant(),
            startDate = draft.reservationPeriod.startDate.value,
            endDate = draft.reservationPeriod.endDate.value,
            maskedVehicleNumber = draft.vehicleNumber.maskedForHistory(),
            status = status,
        )
        writeEntries(entries() + entry)
    }

    override fun clear() {
        preferences.edit().remove(KEY_ENTRIES).apply()
    }

    private fun readEntries(): List<ReservationHistoryEntry> = runCatching {
        val array = JSONArray(preferences.getString(KEY_ENTRIES, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(::entryFromJson)?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private fun entryFromJson(json: JSONObject): ReservationHistoryEntry? = runCatching {
        ReservationHistoryEntry(
            attemptedAt = Instant.parse(json.getString(KEY_ATTEMPTED_AT)),
            startDate = LocalDate.parse(json.getString(KEY_START_DATE)),
            endDate = LocalDate.parse(json.getString(KEY_END_DATE)),
            maskedVehicleNumber = json.getString(KEY_MASKED_VEHICLE),
            status = ReservationHistoryStatus.valueOf(json.getString(KEY_STATUS)),
        )
    }.getOrNull()

    private fun writeEntries(entries: List<ReservationHistoryEntry>) {
        val array = JSONArray()
        entries.sortedByDescending(ReservationHistoryEntry::attemptedAt).forEach { entry ->
            array.put(
                JSONObject()
                    .put(KEY_ATTEMPTED_AT, entry.attemptedAt.toString())
                    .put(KEY_START_DATE, entry.startDate.toString())
                    .put(KEY_END_DATE, entry.endDate.toString())
                    .put(KEY_MASKED_VEHICLE, entry.maskedVehicleNumber)
                    .put(KEY_STATUS, entry.status.name),
            )
        }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "reservation_history"
        const val KEY_ENTRIES = "entries"
        const val KEY_ATTEMPTED_AT = "attempted_at"
        const val KEY_START_DATE = "start_date"
        const val KEY_END_DATE = "end_date"
        const val KEY_MASKED_VEHICLE = "masked_vehicle"
        const val KEY_STATUS = "status"
    }
}
