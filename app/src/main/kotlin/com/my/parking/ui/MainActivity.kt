package com.my.parking.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.my.parking.MyParkingApplication
import com.my.parking.R
import com.my.parking.domain.ReservationDate
import com.my.parking.domain.ReservationDraft
import com.my.parking.domain.ReservationDurationOption
import com.my.parking.domain.ReservationPeriod
import com.my.parking.domain.VehicleNumber
import com.my.parking.domain.VehiclePreferences
import com.my.parking.domain.VisitorName
import java.time.LocalDate

/** Entry screen for choosing the reservation date and vehicle number. */
class MainActivity : AppCompatActivity() {
    private var selectedStartDate = ReservationDate.today()
    private var selectedDurationOption = ReservationDurationOption.ONE_DAY
    private var selectedEndDate: ReservationDate? = null
    private var savedVehicleNumbers = emptyList<String>()
    private lateinit var startDateValue: TextView
    private lateinit var endDateValue: TextView
    private lateinit var durationSpinner: Spinner
    private lateinit var statusMessage: TextView
    private lateinit var registerButton: Button
    private lateinit var remainingTimeValue: TextView
    private lateinit var refreshRemainingTimeButton: Button
    private lateinit var visitorNameInput: EditText
    private lateinit var vehicleNumberInput: EditText
    private lateinit var savedVehicleSpinner: Spinner
    private lateinit var vehicleManagementContent: View
    private lateinit var vehicleManagementToggle: Button
    private lateinit var vehiclePreferences: VehiclePreferences
    private var remainingTimeRefreshInProgress = false
    private val remainingTimeRefresh = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { activityResult ->
        remainingTimeRefreshInProgress = false
        refreshRemainingTimeButton.isEnabled = true
        when (activityResult.data?.getStringExtra(RemainingTimeActivity.EXTRA_STATUS)) {
            RemainingTimeActivity.STATUS_AVAILABLE -> {
                remainingTimeValue.text = "남은 시간: ${activityResult.data?.getStringExtra(RemainingTimeActivity.EXTRA_VALUE)}"
            }
            RemainingTimeActivity.STATUS_LOGIN_REQUIRED -> {
                remainingTimeValue.text = "남은 시간: 공식 사이트 로그인 후 확인할 수 있어요."
            }
            RemainingTimeActivity.STATUS_NETWORK_ERROR -> {
                remainingTimeValue.text = "남은 시간: 네트워크 오류로 확인하지 못했어요."
            }
            else -> remainingTimeValue.text = "남은 시간: 현재 정보를 확인할 수 없어요."
        }
        refreshRemainingTimeButton.text = "새로고침"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.main_toolbar).also { toolbar ->
            applyStatusBarInset(toolbar)
            toolbar.setOnMenuItemClickListener(::onToolbarItemSelected)
        }

        startDateValue = findViewById(R.id.reservation_start_date_value)
        endDateValue = findViewById(R.id.reservation_end_date_value)
        durationSpinner = findViewById(R.id.reservation_duration_spinner)
        statusMessage = findViewById(R.id.status_message)
        registerButton = findViewById(R.id.register_button)
        remainingTimeValue = findViewById(R.id.remaining_time_value)
        refreshRemainingTimeButton = findViewById(R.id.refresh_remaining_time_button)
        visitorNameInput = findViewById(R.id.visitor_name_input)
        vehicleNumberInput = findViewById(R.id.vehicle_number_input)
        savedVehicleSpinner = findViewById(R.id.saved_vehicle_spinner)
        vehicleManagementContent = findViewById(R.id.vehicle_management_content)
        vehicleManagementToggle = findViewById(R.id.vehicle_management_toggle)
        vehiclePreferences = (application as MyParkingApplication).appContainer.vehiclePreferences
        configureDurationSpinner()
        configureSavedVehicleSpinner()
        renderSavedVehicles()
        renderDates()

        findViewById<View>(R.id.select_start_date_button).setOnClickListener {
            showDatePicker(selectedStartDate.value, LocalDate.now(), null, ::selectStartDate)
        }
        findViewById<View>(R.id.select_end_date_button).setOnClickListener {
            val currentEndDate = currentReservationPeriod().endDate.value
            showDatePicker(
                currentEndDate,
                selectedStartDate.value,
                selectedStartDate.value.plusDays(ReservationPeriod.MAX_DURATION_DAYS - 1L),
                ::selectEndDate,
            )
        }
        findViewById<View>(R.id.save_vehicle_button).setOnClickListener { saveVehicleNumber() }
        refreshRemainingTimeButton.setOnClickListener { refreshRemainingTime() }
        findViewById<View>(R.id.delete_vehicle_button).setOnClickListener { deleteVehicleNumber() }
        vehicleManagementToggle.setOnClickListener { toggleVehicleManagement() }
        registerButton.setOnClickListener { prepareReservation() }
        findViewById<View>(R.id.open_reservation_site_button).setOnClickListener {
            startActivity(Intent(this, ReservationWebActivity::class.java))
        }
    }

    private fun onToolbarItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.open_reservation_history -> {
            startActivity(Intent(this, ReservationHistoryActivity::class.java))
            true
        }
        else -> false
    }

    private fun configureSavedVehicleSpinner() {
        savedVehicleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                savedVehicleNumbers.getOrNull(position)?.let { selectedVehicle ->
                    vehicleNumberInput.setText(selectedVehicle)
                    VehicleNumber.of(selectedVehicle).let { vehicleNumber ->
                        vehiclePreferences.saveDefaultVehicleNumber(vehicleNumber)
                        visitorNameInput.setText(vehiclePreferences.visitorNameFor(vehicleNumber)?.value.orEmpty())
                        selectedStartDate = ReservationDate.today()
                        selectedDurationOption = vehiclePreferences.reservationDurationOptionFor(vehicleNumber)
                            ?: ReservationDurationOption.ONE_DAY
                        selectedEndDate = null
                        renderDates()
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
    }

    private fun applyStatusBarInset(toolbar: View) {
        val originalTopPadding = toolbar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            view.updatePadding(
                top = originalTopPadding + insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
            )
            insets
        }
        ViewCompat.requestApplyInsets(toolbar)
    }

    private fun configureDurationSpinner() {
        durationSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            ReservationDurationOption.entries.map(ReservationDurationOption::displayText),
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        durationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedDurationOption = ReservationDurationOption.entries[position]
                selectedEndDate = null
                renderDates()
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
    }

    private fun renderSavedVehicles(selectedVehicle: String? = vehiclePreferences.defaultVehicleNumber()) {
        savedVehicleNumbers = vehiclePreferences.vehicleNumbers()
        findViewById<View>(R.id.saved_vehicle_label).visibility =
            if (savedVehicleNumbers.isEmpty()) View.GONE else View.VISIBLE
        savedVehicleSpinner.visibility = if (savedVehicleNumbers.isEmpty()) View.GONE else View.VISIBLE
        if (savedVehicleNumbers.isEmpty()) return

        val vehicleLabels = savedVehicleNumbers.map { savedVehicle ->
            val name = VehicleNumber.of(savedVehicle)
                .let(vehiclePreferences::visitorNameFor)
                ?.value
                .orEmpty()
            if (name.isBlank()) savedVehicle else "$savedVehicle ($name)"
        }
        savedVehicleSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            vehicleLabels,
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        savedVehicleSpinner.setSelection(savedVehicleNumbers.indexOf(selectedVehicle).coerceAtLeast(0))
    }

    private fun showDatePicker(
        current: LocalDate,
        minDate: LocalDate,
        maxDate: LocalDate?,
        onDateSelected: (LocalDate) -> Unit,
    ) {
        DatePickerDialog(
            this,
            { _, year, month, day -> onDateSelected(LocalDate.of(year, month + 1, day)) },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth,
        ).apply {
            datePicker.minDate = minDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            maxDate?.let {
                datePicker.maxDate = it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }.show()
    }

    private fun selectStartDate(date: LocalDate) {
        runCatching { ReservationDate.of(date) }
            .onSuccess {
                selectedStartDate = it
                selectedEndDate = selectedEndDate?.takeIf { endDate ->
                    !endDate.value.isBefore(it.value) &&
                        !endDate.value.isAfter(it.value.plusDays(ReservationPeriod.MAX_DURATION_DAYS - 1L))
                }
                renderDates()
                statusMessage.visibility = View.GONE
            }
            .onFailure { showError(it.message ?: getString(R.string.unknown_error)) }
    }

    private fun selectEndDate(date: LocalDate) {
        runCatching {
            ReservationPeriod.of(selectedStartDate, ReservationDate.of(date)).endDate
        }.onSuccess {
            selectedEndDate = it
            renderDates()
            statusMessage.visibility = View.GONE
        }.onFailure { showError(it.message ?: getString(R.string.unknown_error)) }
    }

    private fun renderDates() {
        startDateValue.text = "시작일: ${selectedStartDate.displayText()}"
        val period = currentReservationPeriod()
        val selectionLabel = if (selectedEndDate == null) "" else " (직접 선택)"
        endDateValue.text = "종료일: ${period.endDate.displayText()}$selectionLabel"
        if (selectedEndDate == null) {
            val selectedPosition = ReservationDurationOption.entries.indexOf(selectedDurationOption)
            if (durationSpinner.selectedItemPosition != selectedPosition) {
                durationSpinner.setSelection(selectedPosition)
            }
        }
    }

    private fun saveVehicleNumber() {
        runCatching(::currentDraft)
            .onSuccess { draft ->
                vehiclePreferences.saveReservationDraft(draft)
                renderSavedVehicles(draft.vehicleNumber.value)
                showStatus(getString(R.string.vehicle_and_period_saved_message), R.drawable.bg_status_success)
                refreshRemainingTime()
            }
            .onFailure { showError(it.message ?: getString(R.string.unknown_error)) }
    }

    private fun refreshRemainingTime() {
        if (remainingTimeRefreshInProgress) return
        remainingTimeRefreshInProgress = true
        remainingTimeValue.text = "남은 시간: 확인 중…"
        refreshRemainingTimeButton.isEnabled = false
        refreshRemainingTimeButton.text = "조회 중"
        remainingTimeRefresh.launch(RemainingTimeActivity.intentFor(this))
    }

    private fun deleteVehicleNumber() {
        runCatching { VehicleNumber.of(vehicleNumberInput.text.toString()) }
            .onSuccess { vehicleNumber ->
                vehiclePreferences.removeVehicleNumber(vehicleNumber)
                visitorNameInput.text?.clear()
                vehicleNumberInput.text.clear()
                renderSavedVehicles()
                showStatus(getString(R.string.vehicle_deleted_message), R.drawable.bg_status_info)
            }
            .onFailure { showError(it.message ?: getString(R.string.unknown_error)) }
    }

    private fun prepareReservation() {
        runCatching(::currentDraft).onSuccess { draft ->
            vehiclePreferences.saveReservationDraft(draft)
            startActivity(ReservationWebActivity.intentFor(this, draft))
        }.onFailure { showError(it.message ?: getString(R.string.unknown_error)) }
    }

    private fun currentDraft(): ReservationDraft = ReservationDraft(
        visitorName = VisitorName.of(visitorNameInput.text.toString()),
        vehicleNumber = VehicleNumber.of(vehicleNumberInput.text.toString()),
        durationOption = selectedEndDate?.let { null } ?: selectedDurationOption,
        reservationPeriod = currentReservationPeriod(),
    )

    private fun currentReservationPeriod(): ReservationPeriod =
        selectedEndDate?.let { ReservationPeriod.of(selectedStartDate, it) }
            ?: ReservationPeriod.of(selectedStartDate, selectedDurationOption)

    private fun toggleVehicleManagement() {
        val isExpanded = vehicleManagementContent.visibility == View.VISIBLE
        vehicleManagementContent.visibility = if (isExpanded) View.GONE else View.VISIBLE
        vehicleManagementToggle.text = getString(
            if (isExpanded) R.string.vehicle_management_expand else R.string.vehicle_management_collapse,
        )
    }

    private fun showStatus(message: String, backgroundResource: Int) {
        statusMessage.visibility = View.VISIBLE
        statusMessage.setBackgroundResource(backgroundResource)
        statusMessage.text = message
    }

    private fun showError(message: String) {
        showStatus(message, R.drawable.bg_status_error)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
