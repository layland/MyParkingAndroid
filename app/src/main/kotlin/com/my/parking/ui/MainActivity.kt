package com.my.parking.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.my.parking.MyParkingApplication
import com.my.parking.R
import com.my.parking.domain.ReservationDate
import com.my.parking.domain.ReservationDraft
import com.my.parking.domain.ReservationPeriod
import com.my.parking.domain.VehicleNumber
import com.my.parking.domain.VehiclePreferences
import java.time.LocalDate

/** Entry screen for choosing the reservation date and vehicle number. */
class MainActivity : AppCompatActivity() {
    private var selectedStartDate = ReservationDate.today()
    private var selectedEndDate = ReservationDate.today()
    private var savedVehicleNumbers = emptyList<String>()
    private lateinit var startDateValue: TextView
    private lateinit var endDateValue: TextView
    private lateinit var statusMessage: TextView
    private lateinit var registerButton: Button
    private lateinit var vehicleNumberInput: EditText
    private lateinit var savedVehicleSpinner: Spinner
    private lateinit var vehiclePreferences: VehiclePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startDateValue = findViewById(R.id.reservation_start_date_value)
        endDateValue = findViewById(R.id.reservation_end_date_value)
        statusMessage = findViewById(R.id.status_message)
        registerButton = findViewById(R.id.register_button)
        vehicleNumberInput = findViewById(R.id.vehicle_number_input)
        savedVehicleSpinner = findViewById(R.id.saved_vehicle_spinner)
        vehiclePreferences = (application as MyParkingApplication).appContainer.vehiclePreferences
        configureSavedVehicleSpinner()
        renderSavedVehicles()
        renderDates()

        findViewById<View>(R.id.select_start_date_button).setOnClickListener {
            showDatePicker(selectedStartDate.value, ::selectStartDate)
        }
        findViewById<View>(R.id.select_end_date_button).setOnClickListener {
            showDatePicker(selectedEndDate.value, ::selectEndDate)
        }
        findViewById<View>(R.id.save_vehicle_button).setOnClickListener { saveVehicleNumber() }
        findViewById<View>(R.id.delete_vehicle_button).setOnClickListener { deleteVehicleNumber() }
        registerButton.setOnClickListener { prepareReservation() }
        findViewById<View>(R.id.open_reservation_site_button).setOnClickListener {
            startActivity(Intent(this, ReservationWebActivity::class.java))
        }
    }

    private fun configureSavedVehicleSpinner() {
        savedVehicleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                savedVehicleNumbers.getOrNull(position)?.let { selectedVehicle ->
                    vehicleNumberInput.setText(selectedVehicle)
                    VehicleNumber.of(selectedVehicle).let(vehiclePreferences::saveDefaultVehicleNumber)
                }
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

        savedVehicleSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            savedVehicleNumbers,
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        savedVehicleSpinner.setSelection(savedVehicleNumbers.indexOf(selectedVehicle).coerceAtLeast(0))
    }

    private fun showDatePicker(current: LocalDate, onDateSelected: (LocalDate) -> Unit) {
        DatePickerDialog(
            this,
            { _, year, month, day -> onDateSelected(LocalDate.of(year, month + 1, day)) },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth,
        ).show()
    }

    private fun selectStartDate(date: LocalDate) {
        runCatching { ReservationDate.of(date) }
            .onSuccess {
                selectedStartDate = it
                renderDates()
                statusMessage.visibility = View.GONE
            }
            .onFailure { showError(it.message ?: getString(R.string.unknown_error)) }
    }

    private fun selectEndDate(date: LocalDate) {
        runCatching { ReservationDate.of(date) }
            .onSuccess {
                selectedEndDate = it
                renderDates()
                statusMessage.visibility = View.GONE
            }
            .onFailure { showError(it.message ?: getString(R.string.unknown_error)) }
    }

    private fun renderDates() {
        startDateValue.text = selectedStartDate.displayText()
        endDateValue.text = selectedEndDate.displayText()
    }

    private fun saveVehicleNumber() {
        runCatching { VehicleNumber.of(vehicleNumberInput.text.toString()) }
            .onSuccess { vehicleNumber ->
                vehiclePreferences.saveDefaultVehicleNumber(vehicleNumber)
                renderSavedVehicles(vehicleNumber.value)
                statusMessage.visibility = View.VISIBLE
                statusMessage.text = getString(R.string.vehicle_saved_message)
            }
            .onFailure { showError(it.message ?: getString(R.string.unknown_error)) }
    }

    private fun deleteVehicleNumber() {
        runCatching { VehicleNumber.of(vehicleNumberInput.text.toString()) }
            .onSuccess { vehicleNumber ->
                vehiclePreferences.removeVehicleNumber(vehicleNumber)
                vehicleNumberInput.text.clear()
                renderSavedVehicles()
                statusMessage.visibility = View.VISIBLE
                statusMessage.text = getString(R.string.vehicle_deleted_message)
            }
            .onFailure { showError(it.message ?: getString(R.string.unknown_error)) }
    }

    private fun prepareReservation() {
        runCatching {
            ReservationDraft(
                vehicleNumber = VehicleNumber.of(vehicleNumberInput.text.toString()),
                reservationPeriod = ReservationPeriod.of(selectedStartDate, selectedEndDate),
            )
        }.onSuccess { draft ->
            vehiclePreferences.saveDefaultVehicleNumber(draft.vehicleNumber)
            startActivity(ReservationWebActivity.intentFor(this, draft))
        }.onFailure { showError(it.message ?: getString(R.string.unknown_error)) }
    }

    private fun showError(message: String) {
        statusMessage.visibility = View.VISIBLE
        statusMessage.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
