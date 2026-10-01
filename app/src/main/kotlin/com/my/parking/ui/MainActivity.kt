package com.my.parking.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.my.parking.MyParkingApplication
import com.my.parking.domain.ReservationPeriod
import java.time.LocalDate
import kotlinx.coroutines.flow.collect

/** Compose entry screen for selecting a reservation period and a saved vehicle. */
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels {
        val preferences = (application as MyParkingApplication).appContainer.vehiclePreferences
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(preferences) as T
        }
    }
    private lateinit var remainingTimeLoader: RemainingTimeLoader
    private val historyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val vehicleNumber = result.data?.getStringExtra(HISTORY_VEHICLE_NUMBER) ?: return@registerForActivityResult
        viewModel.loadVehicleFromHistory(vehicleNumber, result.data?.getStringExtra(HISTORY_VISITOR_NAME).orEmpty())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        remainingTimeLoader = RemainingTimeLoader(this, viewModel::renderRemainingTime)
        setContent {
            MyParkingTheme {
                val state by viewModel.uiState.collectAsState()
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    viewModel.messages.collect { message ->
                        Toast.makeText(this@MainActivity, message.text, Toast.LENGTH_SHORT).show()
                    }
                }
                MainScreen(
                    state = state,
                    onVisitorNameChange = viewModel::updateVisitorName,
                    onVehicleNumberChange = viewModel::updateVehicleNumber,
                    onStartDateClick = { showDatePicker(state.startDate.value, LocalDate.now(), null, viewModel::selectStartDate) },
                    onEndDateClick = {
                        showDatePicker(
                            state.period.endDate.value,
                            state.startDate.value,
                            state.startDate.value.plusDays(ReservationPeriod.MAX_DURATION_DAYS - 1L),
                            viewModel::selectEndDate,
                        )
                    },
                    onQuickDateSelect = viewModel::selectQuickPeriodEnding,
                    onSaveVehicle = viewModel::saveVehicle,
                    onRefreshRemainingTime = ::refreshRemainingTime,
                    onOpenHistory = { historyLauncher.launch(Intent(this, ReservationHistoryActivity::class.java)) },
                    onOpenReservationSite = { startActivity(Intent(this, ReservationWebActivity::class.java)) },
                    onRegister = { viewModel.reservationDraftOrNull()?.let { startActivity(ReservationWebActivity.intentFor(this, it)) } },
                    onVehicleSelected = viewModel::selectVehicle,
                    onDeleteVehicle = viewModel::deleteCurrentVehicle,
                )
            }
        }
        refreshRemainingTime()
    }

    private fun refreshRemainingTime() {
        if (viewModel.beginRemainingTimeRefresh()) remainingTimeLoader.refresh()
    }

    private fun showDatePicker(current: LocalDate, minimum: LocalDate, maximum: LocalDate?, onSelected: (LocalDate) -> Unit) {
        DatePickerDialog(this, { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) }, current.year, current.monthValue - 1, current.dayOfMonth)
            .apply {
                datePicker.minDate = minimum.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                maximum?.let { datePicker.maxDate = it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
            }.show()
    }

    override fun onDestroy() {
        if (::remainingTimeLoader.isInitialized) remainingTimeLoader.cancel()
        super.onDestroy()
    }

    private companion object {
        const val HISTORY_VEHICLE_NUMBER = "history_vehicle_number"
        const val HISTORY_VISITOR_NAME = "history_visitor_name"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    state: MainUiState,
    onVisitorNameChange: (String) -> Unit,
    onVehicleNumberChange: (String) -> Unit,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    onQuickDateSelect: (LocalDate) -> Unit,
    onSaveVehicle: () -> Unit,
    onRefreshRemainingTime: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenReservationSite: () -> Unit,
    onRegister: () -> Unit,
    onVehicleSelected: (String) -> Unit,
    onDeleteVehicle: () -> Unit,
) {
    var vehiclesVisible by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("방문 주차 예약", fontWeight = FontWeight.SemiBold) },
                actions = { TextButton(onClick = onOpenHistory) { Text("예약 이력") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                Button(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text("방문예약 등록") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenReservationSite, modifier = Modifier.fillMaxWidth()) { Text("공식 예약 사이트만 열기") }
                Spacer(Modifier.height(8.dp))
                Text(
                    "경선♡",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(state.remainingTime, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("공식 사이트에 로그인된 경우에만 표시됩니다.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onRefreshRemainingTime, enabled = !state.isRefreshingRemainingTime) {
                        Text(if (state.isRefreshingRemainingTime) "조회 중" else "새로고침")
                    }
                }
            }
            item { PeriodCard(state, onStartDateClick, onEndDateClick, onQuickDateSelect) }
            item {
                VehicleCard(
                    state = state,
                    onVisitorNameChange = onVisitorNameChange,
                    onVehicleNumberChange = onVehicleNumberChange,
                    onOpenVehicles = { vehiclesVisible = true },
                    onSaveVehicle = onSaveVehicle,
                )
            }
        }
    }
    if (vehiclesVisible) {
        ModalBottomSheet(onDismissRequest = { vehiclesVisible = false }) {
            SavedVehiclesSheet(
                vehicles = state.savedVehicles,
                selectedNumber = state.vehicleNumber,
                onSelect = { onVehicleSelected(it); vehiclesVisible = false },
                onDelete = { onDeleteVehicle(); vehiclesVisible = false },
            )
        }
    }
}

@Composable
private fun PeriodCard(state: MainUiState, onStartClick: () -> Unit, onEndClick: () -> Unit, onQuickDateSelect: (LocalDate) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("예약 기간", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DateRow(
                    label = "시작일",
                    value = state.startDate.displayText(),
                    onClick = onStartClick,
                    modifier = Modifier.weight(1f),
                )
                DateRow(
                    label = "종료일",
                    value = state.period.endDate.displayText(),
                    onClick = onEndClick,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("오늘" to 0L, "내일까지" to 1L, "모레까지" to 2L).forEach { (label, offset) ->
                    FilterChip(
                        selected = state.startDate.value == LocalDate.now().plusDays(offset) && state.period.durationDays == 1,
                        onClick = { onQuickDateSelect(LocalDate.now().plusDays(offset)) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text("${state.startDate.displayText()} ~ ${state.period.endDate.displayText()} · ${state.period.durationDays}일", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DateRow(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) = Row(
    modifier = modifier.clickable(onClick = onClick).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 16.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Text(label, modifier = Modifier.alignByBaseline(), maxLines = 1)
    Text(value, modifier = Modifier.alignByBaseline(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun VehicleCard(state: MainUiState, onVisitorNameChange: (String) -> Unit, onVehicleNumberChange: (String) -> Unit, onOpenVehicles: () -> Unit, onSaveVehicle: () -> Unit) {
    Card {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("방문 차량", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onOpenVehicles) { Text("저장 차량 ${state.savedVehicles.size}대") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = state.visitorName, onValueChange = onVisitorNameChange, modifier = Modifier.fillMaxWidth(), label = { Text("방문자 이름 (선택)") }, singleLine = true)
                    VehicleNumberField(
                        number = state.vehicleNumber,
                        onNumberChange = onVehicleNumberChange,
                    )
                }
                Button(onClick = onSaveVehicle, modifier = Modifier.padding(start = 8.dp)) { Text("차량 저장") }
            }
        }
    }
}

/**
 * Keeps cursor/selection state in the UI while exposing only text changes to the ViewModel.
 * Selects the existing number once on focus entry, allowing later taps to position the cursor.
 */
@Composable
private fun VehicleNumberField(number: String, onNumberChange: (String) -> Unit) {
    var fieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(number))
    }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(number) {
        if (fieldValue.text != number) {
            fieldValue = TextFieldValue(
                text = number,
                selection = if (isFocused) TextRange(0, number.length) else TextRange(number.length),
            )
        }
    }
    LaunchedEffect(isFocused) {
        if (isFocused) {
            // Apply after the pointer event that acquired focus has positioned the cursor.
            withFrameNanos { }
            fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { updated ->
            val textChanged = updated.text != fieldValue.text
            fieldValue = updated
            if (textChanged) onNumberChange(updated.text)
        },
        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
        label = { Text("차량번호") },
        singleLine = true,
    )
}

@Composable
private fun SavedVehiclesSheet(vehicles: List<SavedVehicle>, selectedNumber: String, onSelect: (String) -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
        Text("저장 차량", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        if (vehicles.isEmpty()) Text("저장된 차량이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        vehicles.forEach { vehicle ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(vehicle.number) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(vehicle.number, fontWeight = if (vehicle.number == selectedNumber) FontWeight.Bold else FontWeight.Normal)
                    if (vehicle.visitorName.isNotBlank()) Text(vehicle.visitorName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (vehicle.number == selectedNumber) Text("선택됨", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            HorizontalDivider()
        }
        if (selectedNumber.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDelete) { Text("현재 차량번호 삭제", color = MaterialTheme.colorScheme.error) }
        }
    }
}
