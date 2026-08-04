package com.my.parking.ui

import androidx.lifecycle.ViewModel
import com.my.parking.domain.RemainingTimeResult
import com.my.parking.domain.ReservationDate
import com.my.parking.domain.ReservationDraft
import com.my.parking.domain.ReservationDurationOption
import com.my.parking.domain.ReservationPeriod
import com.my.parking.domain.VehicleNumber
import com.my.parking.domain.VehiclePreferences
import com.my.parking.domain.VisitorName
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PeriodMode { DURATION, END_DATE }
enum class MessageKind { INFO, SUCCESS, ERROR }

data class UiMessage(val text: String, val kind: MessageKind)
data class SavedVehicle(val number: String, val visitorName: String)

data class MainUiState(
    val startDate: ReservationDate = ReservationDate.today(),
    val duration: ReservationDurationOption = ReservationDurationOption.ONE_DAY,
    val endDate: ReservationDate? = null,
    val periodMode: PeriodMode = PeriodMode.DURATION,
    val visitorName: String = "",
    val vehicleNumber: String = "",
    val savedVehicles: List<SavedVehicle> = emptyList(),
    val remainingTime: String = "남은 시간: 확인 중",
    val isRefreshingRemainingTime: Boolean = false,
) {
    val period: ReservationPeriod get() = endDate?.let { ReservationPeriod.of(startDate, it) }
        ?: ReservationPeriod.of(startDate, duration)
}

class MainViewModel(private val vehiclePreferences: VehiclePreferences) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()
    private val mutableMessages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<UiMessage> = mutableMessages

    init {
        reloadVehicles()
        vehiclePreferences.defaultVehicleNumber()?.let(::selectVehicle)
    }

    fun updateVisitorName(value: String) = mutableUiState.update { it.copy(visitorName = value) }

    fun updateVehicleNumber(value: String) = mutableUiState.update { state ->
        state.copy(vehicleNumber = value, visitorName = if (value == state.vehicleNumber) state.visitorName else "")
    }

    fun selectStartDate(value: LocalDate) = runCatching { ReservationDate.of(value) }
        .onSuccess { start ->
            mutableUiState.update { state ->
                state.copy(
                    startDate = start,
                    endDate = state.endDate?.takeIf { !it.value.isBefore(start.value) && !it.value.isAfter(start.value.plusDays(ReservationPeriod.MAX_DURATION_DAYS - 1L)) },
                )
            }
        }.onFailure(::showError)

    fun selectEndDate(value: LocalDate) = runCatching { ReservationDate.of(value) }
        .map { ReservationPeriod.of(uiState.value.startDate, it).endDate }
        .onSuccess { end -> mutableUiState.update { it.copy(endDate = end, periodMode = PeriodMode.END_DATE) } }
        .onFailure(::showError)

    fun selectDuration(value: ReservationDurationOption) = mutableUiState.update {
        it.copy(duration = value, endDate = null, periodMode = PeriodMode.DURATION)
    }

    fun selectPeriodMode(value: PeriodMode) = mutableUiState.update {
        it.copy(periodMode = value, endDate = if (value == PeriodMode.DURATION) null else it.period.endDate)
    }

    fun saveVehicle() = runCatching(::draft).onSuccess { draft ->
        vehiclePreferences.saveReservationDraft(draft)
        reloadVehicles()
        showMessage("차량번호를 저장했습니다.", MessageKind.SUCCESS)
    }.onFailure(::showError)

    fun selectVehicle(number: String) = runCatching { VehicleNumber.of(number) }.onSuccess { vehicle ->
        vehiclePreferences.saveDefaultVehicleNumber(vehicle)
        mutableUiState.update {
            it.copy(
                vehicleNumber = vehicle.value,
                visitorName = vehiclePreferences.visitorNameFor(vehicle)?.value.orEmpty(),
                startDate = ReservationDate.today(),
                duration = ReservationDurationOption.ONE_DAY,
                endDate = null,
                periodMode = PeriodMode.DURATION,
            )
        }
    }.onFailure(::showError)

    fun deleteCurrentVehicle() = runCatching { VehicleNumber.of(uiState.value.vehicleNumber) }.onSuccess { vehicle ->
        vehiclePreferences.removeVehicleNumber(vehicle)
        mutableUiState.update { it.copy(vehicleNumber = "", visitorName = "") }
        reloadVehicles()
        showMessage("차량번호를 삭제했습니다.", MessageKind.INFO)
    }.onFailure(::showError)

    fun beginRemainingTimeRefresh(): Boolean {
        if (uiState.value.isRefreshingRemainingTime) return false
        mutableUiState.update { it.copy(remainingTime = "남은 시간: 확인 중", isRefreshingRemainingTime = true) }
        return true
    }

    fun renderRemainingTime(result: RemainingTimeResult) = mutableUiState.update {
        it.copy(
            isRefreshingRemainingTime = false,
            remainingTime = when (result) {
                is RemainingTimeResult.Available -> "남은 시간: ${result.displayText}"
                RemainingTimeResult.LoginRequired -> "남은 시간: 공식 사이트 로그인 필요"
                RemainingTimeResult.NetworkError -> "남은 시간: 네트워크 오류"
                RemainingTimeResult.Unavailable -> "남은 시간: 현재 정보를 확인할 수 없음"
            },
        )
    }

    fun reservationDraftOrNull(): ReservationDraft? = runCatching(::draft).getOrElse { showError(it); null }

    private fun draft(): ReservationDraft {
        val state = uiState.value
        return ReservationDraft(
            visitorName = VisitorName.of(state.visitorName),
            vehicleNumber = VehicleNumber.of(state.vehicleNumber),
            durationOption = if (state.periodMode == PeriodMode.DURATION) state.duration else null,
            reservationPeriod = state.period,
        )
    }

    private fun reloadVehicles() = mutableUiState.update {
        it.copy(savedVehicles = vehiclePreferences.vehicleNumbers().map { number ->
            val visitorName = VehicleNumber.of(number).let(vehiclePreferences::visitorNameFor)?.value.orEmpty()
            SavedVehicle(number, visitorName)
        })
    }

    private fun showMessage(text: String, kind: MessageKind) {
        mutableMessages.tryEmit(UiMessage(text, kind))
    }

    private fun showError(error: Throwable) {
        showMessage(error.message ?: "알 수 없는 오류가 발생했어요.", MessageKind.ERROR)
    }
}
