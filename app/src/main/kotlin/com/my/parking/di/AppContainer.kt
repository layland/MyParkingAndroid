package com.my.parking.di

import android.content.Context
import com.my.parking.data.SharedPreferencesReservationHistoryRepository
import com.my.parking.data.SharedPreferencesVehiclePreferences
import com.my.parking.domain.ReservationHistoryRepository
import com.my.parking.domain.VehiclePreferences

/** Composition root; UI depends on the VehiclePreferences interface. */
class AppContainer(context: Context) {
    val vehiclePreferences: VehiclePreferences = SharedPreferencesVehiclePreferences(context)
    val familySharingRepository = com.my.parking.sharing.FamilySharingRepository(com.my.parking.sharing.SecureFamilyStore(context))
    val reservationHistoryRepository: com.my.parking.sharing.SharingHistoryRepository =
        com.my.parking.sharing.SharingHistoryRepository(SharedPreferencesReservationHistoryRepository(context), familySharingRepository)
}

