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
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.my.parking.MyParkingApplication
import com.my.parking.R
import com.my.parking.domain.ReservationDate
import com.my.parking.domain.ReservationDraft
import com.my.parking.domain.ReservationDurationOption
import com.my.parking.domain.ReservationPeriod
import com.my.parking.domain.RemainingTimeResult
import com.my.parking.domain.VehicleNumber
import com.my.parking.domain.VehiclePreferences
import com.my.parking.domain.VisitorName
import java.time.LocalDate

/** Entry screen for choosing the reservation date and vehicle number. */
class MainActivity : AppCompatActivity() {
    private enum class PeriodSelectionMode { DURATION, END_DATE }

    private var selectedStartDate = ReservationDate.today()
    private var selectedDurationOption = ReservationDurationOption.ONE_DAY
    private var selectedEndDate: ReservationDate? = null
    private var periodSelectionMode = PeriodSelectionMode.DURATION
    private var savedVehicleNumbers = emptyList<String>()
    private lateinit var periodSummaryValue: TextView
    private lateinit var durationSpinner: Spinner
    private lateinit var periodModeToggle: MaterialButtonToggleGroup
    private lateinit var selectEndDateButton: View
    private lateinit var statusMessage: TextView
    private lateinit var registerButton: Button
    private lateinit var remainingTimeValue: TextView
    private lateinit var refreshRemainingTimeButton: Button
    private lateinit var mainToolbar: com.google.android.material.appbar.MaterialToolbar
    private lateinit var mainDrawer: DrawerLayout
    private lateinit var visitorNameInput: MaterialAutoCompleteTextView
    private lateinit var vehicleNumberInput: EditText
    private lateinit var savedVehicleList: ListView
    private lateinit var vehicleManagementContent: View
    private lateinit var vehicleManagementToggle: Button
    private lateinit var vehiclePreferences: VehiclePreferences
    private var remainingTimeRefreshInProgress = false
    private lateinit var remainingTimeLoader: RemainingTimeLoader
    private var lastVehicleNumberText = ""
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
        mainDrawer = findViewById(R.id.main_drawer)
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.main_toolbar).also { toolbar ->
            mainToolbar = toolbar
            mainToolbar.subtitle = null
            applyStatusBarInset(toolbar)
            toolbar.setOnMenuItemClickListener(::onToolbarItemSelected)
        }

        periodSummaryValue = findViewById(R.id.reservation_period_summary)
        durationSpinner = findViewById(R.id.reservation_duration_spinner)
        periodModeToggle = findViewById(R.id.reservation_period_mode_toggle)
        selectEndDateButton = findViewById(R.id.select_end_date_button)
        statusMessage = findViewById(R.id.status_message)
        registerButton = findViewById(R.id.register_button)
        remainingTimeValue = findViewById(R.id.header_remaining_time_value)
        refreshRemainingTimeButton = findViewById(R.id.header_refresh_remaining_time_button)
        (findViewById<View>(R.id.remaining_time_value).parent.parent as View).visibility = View.GONE
        findViewById<View>(R.id.main_instruction_message).visibility = View.GONE
        visitorNameInput = findViewById(R.id.visitor_name_input)
        vehicleNumberInput = findViewById(R.id.vehicle_number_input)
        lastVehicleNumberText = vehicleNumberInput.text.toString()
        savedVehicleList = findViewById(R.id.drawer_saved_vehicle_list)
        vehicleManagementContent = findViewById(R.id.vehicle_management_content)
        vehicleManagementToggle = findViewById(R.id.vehicle_management_toggle)
        (vehicleManagementToggle.parent.parent as View).visibility = View.GONE
        vehiclePreferences = (application as MyParkingApplication).appContainer.vehiclePreferences
        remainingTimeLoader = RemainingTimeLoader(this, ::renderRemainingTimeResult)
        configureDurationSpinner()
        configurePeriodSelectionMode()
        configureSavedVehicleList()
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
        vehicleNumberInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) vehicleNumberInput.post { vehicleNumberInput.selectAll() }
        }
        vehicleNumberInput.setOnClickListener {
            vehicleNumberInput.post { vehicleNumberInput.selectAll() }
        }
        vehicleNumberInput.doAfterTextChanged { editable ->
            val currentVehicleNumber = editable?.toString().orEmpty()
            if (currentVehicleNumber != lastVehicleNumberText) visitorNameInput.text?.clear()
            lastVehicleNumberText = currentVehicleNumber
        }
        refreshRemainingTimeButton.setOnClickListener { refreshRemainingTimeInBackground() }
        findViewById<View>(R.id.drawer_delete_vehicle_button).setOnClickListener { deleteVehicleNumber() }
        registerButton.setOnClickListener { prepareReservation() }
        findViewById<View>(R.id.open_reservation_site_button).setOnClickListener {
            startActivity(Intent(this, ReservationWebActivity::class.java))
        }
        showToolbarHintOnce()
        refreshRemainingTimeInBackground()
    }

    private fun onToolbarItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.open_vehicle_drawer -> {
            mainDrawer.openDrawer(GravityCompat.END)
            true
        }
        R.id.open_reservation_history -> {
            startActivity(Intent(this, ReservationHistoryActivity::class.java))
            true
        }
        else -> false
    }

    private fun configureSavedVehicleList() {
        savedVehicleList.setOnItemClickListener { _, _, position, _ ->
            savedVehicleNumbers.getOrNull(position)?.let(::loadSavedVehicle)
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

    private fun configurePeriodSelectionMode() {
        periodModeToggle.check(R.id.select_duration_mode_button)
        periodModeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            periodSelectionMode = when (checkedId) {
                R.id.select_end_date_mode_button -> PeriodSelectionMode.END_DATE
                else -> PeriodSelectionMode.DURATION
            }
            if (periodSelectionMode == PeriodSelectionMode.DURATION) selectedEndDate = null
            renderDates()
        }
    }

    private fun renderSavedVehicles(selectedVehicle: String? = vehiclePreferences.defaultVehicleNumber()) {
        savedVehicleNumbers = vehiclePreferences.vehicleNumbers()
        findViewById<View>(R.id.drawer_saved_vehicle_label).visibility =
            if (savedVehicleNumbers.isEmpty()) View.GONE else View.VISIBLE
        savedVehicleList.visibility = if (savedVehicleNumbers.isEmpty()) View.GONE else View.VISIBLE
        if (savedVehicleNumbers.isEmpty()) {
            visitorNameInput.setAdapter(
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, emptyList<String>()),
            )
            return
        }

        val vehicleLabels = savedVehicleNumbers.map { savedVehicle ->
            val name = VehicleNumber.of(savedVehicle)
                .let(vehiclePreferences::visitorNameFor)
                ?.value
                .orEmpty()
            if (name.isBlank()) savedVehicle else "$name · $savedVehicle"
        }
        val vehicleByLabel = vehicleLabels.zip(savedVehicleNumbers).toMap()
        visitorNameInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, vehicleLabels),
        )
        visitorNameInput.setOnClickListener {
            if (savedVehicleNumbers.isNotEmpty()) visitorNameInput.showDropDown()
        }
        visitorNameInput.setOnItemClickListener { parent, _, position, _ ->
            (parent.getItemAtPosition(position) as? String)
                ?.let(vehicleByLabel::get)
                ?.let(::loadSavedVehicle)
        }
        savedVehicleList.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_activated_1,
            vehicleLabels,
        )
        savedVehicleList.setItemChecked(
            savedVehicleNumbers.indexOf(selectedVehicle).coerceAtLeast(0),
            true,
        )
    }

    private fun loadSavedVehicle(selectedVehicle: String) {
        vehicleNumberInput.setText(selectedVehicle)
        VehicleNumber.of(selectedVehicle).let { vehicleNumber ->
            vehiclePreferences.saveDefaultVehicleNumber(vehicleNumber)
            visitorNameInput.setText(vehiclePreferences.visitorNameFor(vehicleNumber)?.value.orEmpty(), false)
            selectedStartDate = ReservationDate.today()
            selectedDurationOption = ReservationDurationOption.ONE_DAY
            selectedEndDate = null
            renderDates()
        }
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
            periodSelectionMode = PeriodSelectionMode.END_DATE
            periodModeToggle.check(R.id.select_end_date_mode_button)
            selectedEndDate = it
            renderDates()
            statusMessage.visibility = View.GONE
        }.onFailure { showError(it.message ?: getString(R.string.unknown_error)) }
    }

    private fun renderDates() {
        val period = currentReservationPeriod()
        durationSpinner.visibility = if (periodSelectionMode == PeriodSelectionMode.DURATION) View.VISIBLE else View.GONE
        selectEndDateButton.visibility = if (periodSelectionMode == PeriodSelectionMode.END_DATE) View.VISIBLE else View.GONE
        periodSummaryValue.text =
            "${selectedStartDate.displayText()} ~ ${period.endDate.displayText()} · ${period.durationDays}일"
        if (periodSelectionMode == PeriodSelectionMode.DURATION) {
            val selectedPosition = ReservationDurationOption.entries.indexOf(selectedDurationOption)
            if (durationSpinner.selectedItemPosition != selectedPosition) {
                durationSpinner.setSelection(selectedPosition)
            }
        }
    }

    private fun showToolbarHintOnce() {
        val preferences = getSharedPreferences("main_ui_hints", MODE_PRIVATE)
        if (preferences.getBoolean("toolbar_actions_seen", false)) return
        mainToolbar.post {
            Snackbar.make(
                mainDrawer,
                "상단 메뉴에서 저장 차량과 예약 이력을 확인할 수 있어요.",
                Snackbar.LENGTH_LONG,
            ).show()
            preferences.edit().putBoolean("toolbar_actions_seen", true).apply()
        }
    }

    private fun saveVehicleNumber() {
        runCatching(::currentDraft)
            .onSuccess { draft ->
                vehiclePreferences.saveReservationDraft(draft)
                renderSavedVehicles(draft.vehicleNumber.value)
                showStatus(getString(R.string.vehicle_saved_message), R.drawable.bg_status_success)
                refreshRemainingTimeInBackground()
            }
            .onFailure { showError(it.message ?: getString(R.string.unknown_error)) }
    }

    private fun refreshRemainingTimeInBackground() {
        if (remainingTimeRefreshInProgress) return
        remainingTimeRefreshInProgress = true
        remainingTimeValue.text = "남은 시간: 확인 중"
        refreshRemainingTimeButton.isEnabled = false
        refreshRemainingTimeButton.text = "조회 중"
        remainingTimeLoader.refresh()
    }

    private fun renderRemainingTimeResult(result: RemainingTimeResult) {
        remainingTimeRefreshInProgress = false
        refreshRemainingTimeButton.isEnabled = true
        refreshRemainingTimeButton.text = "새로고침"
        remainingTimeValue.text = when (result) {
            is RemainingTimeResult.Available -> "남은 시간: ${result.displayText}"
            RemainingTimeResult.LoginRequired -> "남은 시간: 공식 사이트 로그인 필요"
            RemainingTimeResult.NetworkError -> "남은 시간: 네트워크 오류"
            RemainingTimeResult.Unavailable -> "남은 시간: 현재 정보를 확인할 수 없음"
        }
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

    override fun onDestroy() {
        if (::remainingTimeLoader.isInitialized) remainingTimeLoader.cancel()
        super.onDestroy()
    }

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
