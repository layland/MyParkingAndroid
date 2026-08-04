package com.my.parking.ui

import com.my.parking.domain.ReservationDraft
import com.my.parking.domain.VehicleNumber
import com.my.parking.domain.VehiclePreferences
import com.my.parking.domain.VisitorName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainViewModelTest {
    @Test
    fun selectingSavedVehicleResetsPeriodAndLoadsVisitorName() {
        val preferences = FakeVehiclePreferences().apply {
            saveReservationDraft(
                ReservationDraft(
                    visitorName = VisitorName.of("홍길동"),
                    vehicleNumber = VehicleNumber.of("12가3456"),
                    durationOption = null,
                    reservationPeriod = com.my.parking.domain.ReservationPeriod.of(
                        com.my.parking.domain.ReservationDate.today(),
                        com.my.parking.domain.ReservationDurationOption.THREE_DAYS,
                    ),
                ),
            )
        }
        val viewModel = MainViewModel(preferences)

        viewModel.selectVehicle("12가3456")

        assertEquals("12가3456", viewModel.uiState.value.vehicleNumber)
        assertEquals("홍길동", viewModel.uiState.value.visitorName)
        assertEquals(PeriodMode.DURATION, viewModel.uiState.value.periodMode)
        assertNull(viewModel.uiState.value.endDate)
    }

    private class FakeVehiclePreferences : VehiclePreferences {
        private val vehicles = linkedMapOf<String, VisitorName>()
        private var defaultVehicle: String? = null

        override fun defaultVehicleNumber(): String? = defaultVehicle
        override fun vehicleNumbers(): List<String> = vehicles.keys.toList()
        override fun saveDefaultVehicleNumber(vehicleNumber: VehicleNumber) { defaultVehicle = vehicleNumber.value }
        override fun saveReservationDraft(draft: ReservationDraft) {
            vehicles[draft.vehicleNumber.value] = draft.visitorName
            defaultVehicle = draft.vehicleNumber.value
        }
        override fun visitorNameFor(vehicleNumber: VehicleNumber): VisitorName? = vehicles[vehicleNumber.value]
        override fun removeVehicleNumber(vehicleNumber: VehicleNumber) { vehicles.remove(vehicleNumber.value) }
    }
}
