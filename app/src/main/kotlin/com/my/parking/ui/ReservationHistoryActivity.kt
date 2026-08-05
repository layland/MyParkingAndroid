package com.my.parking.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.my.parking.MyParkingApplication
import com.my.parking.domain.ReservationHistoryRepository
import com.my.parking.domain.ReservationHistoryStatus
import com.my.parking.domain.VehicleReservationHistory
import com.my.parking.domain.groupByVehicle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HistoryUiState(val histories: List<VehicleReservationHistory> = emptyList())

class ReservationHistoryViewModel(
    private val repository: ReservationHistoryRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = mutableUiState.asStateFlow()

    init { refresh() }

    fun refresh() { mutableUiState.value = HistoryUiState(repository.entries().groupByVehicle()) }
    fun clear() { repository.clear(); refresh() }

    fun removeVehicleHistory(history: VehicleReservationHistory) {
        repository.removeVehicleHistory(history.vehicleNumber)
        refresh()
    }
}

/** Compose screen for local, server-verified reservation submission results. */
class ReservationHistoryActivity : AppCompatActivity() {
    private val viewModel: ReservationHistoryViewModel by viewModels {
        val container = (application as MyParkingApplication).appContainer
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ReservationHistoryViewModel(
                container.reservationHistoryRepository,
            ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyParkingTheme {
                val state by viewModel.uiState.collectAsState()
                ReservationHistoryScreen(
                    state = state,
                    onBack = ::finish,
                    onClear = viewModel::clear,
                    onDeleteVehicleHistory = viewModel::removeVehicleHistory,
                    onVehicleSelected = ::returnSelectedVehicle,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun returnSelectedVehicle(history: VehicleReservationHistory) {
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(EXTRA_VEHICLE_NUMBER, history.vehicleNumber)
                .putExtra(EXTRA_VISITOR_NAME, history.visitorName),
        )
        finish()
    }

    private companion object {
        const val EXTRA_VEHICLE_NUMBER = "history_vehicle_number"
        const val EXTRA_VISITOR_NAME = "history_visitor_name"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReservationHistoryScreen(
    state: HistoryUiState,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onDeleteVehicleHistory: (VehicleReservationHistory) -> Unit,
    onVehicleSelected: (VehicleReservationHistory) -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var historyToDelete by remember { mutableStateOf<VehicleReservationHistory?>(null) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("예약 이력", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } },
                actions = { TextButton(onClick = { confirmClear = true }, enabled = state.histories.isNotEmpty()) { Text("전체 삭제") } },
            )
        },
    ) { padding ->
        if (state.histories.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("아직 예약 제출 이력이 없습니다", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("예약을 등록한 뒤 서버 응답이 확인되면 이곳에 표시됩니다.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.histories, key = { it.vehicleNumber + it.entries.first().attemptedAt }) {
                    VehicleHistoryCard(it, onVehicleSelected, onLongClick = { historyToDelete = it })
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("예약 이력 전체 삭제") },
            text = { Text("저장된 예약 제출 이력을 모두 삭제할까요?") },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } },
            confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("전체 삭제", color = MaterialTheme.colorScheme.error) } },
        )
    }
    historyToDelete?.let { history ->
        AlertDialog(
            onDismissRequest = { historyToDelete = null },
            title = { Text("차량 예약 이력 삭제") },
            text = { Text("이 차량의 예약 이력을 모두 삭제할까요?") },
            dismissButton = { TextButton(onClick = { historyToDelete = null }) { Text("취소") } },
            confirmButton = {
                TextButton(onClick = { onDeleteVehicleHistory(history); historyToDelete = null }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VehicleHistoryCard(
    history: VehicleReservationHistory,
    onClick: (VehicleReservationHistory) -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier.combinedClickable(
            onClick = { onClick(history) },
            onLongClick = onLongClick,
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(history.vehicleNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (history.visitorName.isNotBlank()) Text(history.visitorName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            history.entries.forEachIndexed { index, entry ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Text("${entry.startDate} ~ ${entry.endDate}", fontWeight = FontWeight.Medium)
                        Text(entry.attemptedAt.atZone(ZoneId.systemDefault()).format(ATTEMPTED_AT_FORMAT), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusChip(entry.status)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: ReservationHistoryStatus) {
    val success = status == ReservationHistoryStatus.SUCCESS
    Text(
        if (success) "등록 성공" else "등록 실패",
        modifier = Modifier.padding(start = 8.dp).then(Modifier),
        color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.labelLarge,
    )
}

private val ATTEMPTED_AT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.KOREA)
