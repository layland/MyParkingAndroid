package com.my.parking.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.my.parking.MyParkingApplication
import com.my.parking.R
import com.my.parking.domain.ReservationHistoryEntry
import com.my.parking.domain.ReservationHistoryRepository
import com.my.parking.domain.ReservationHistoryStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Displays only local records of submission results verified by the server response. */
class ReservationHistoryActivity : AppCompatActivity() {
    private lateinit var historyRepository: ReservationHistoryRepository
    private lateinit var historyList: ListView
    private lateinit var emptyState: View
    private lateinit var clearButton: Button
    private lateinit var adapter: ReservationHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reservation_history)
        historyRepository = (application as MyParkingApplication).appContainer.reservationHistoryRepository
        historyList = findViewById(R.id.reservation_history_list)
        emptyState = findViewById(R.id.reservation_history_empty)
        clearButton = findViewById(R.id.clear_reservation_history_button)
        adapter = ReservationHistoryAdapter(emptyList())
        historyList.adapter = adapter

        findViewById<MaterialToolbar>(R.id.reservation_history_toolbar).apply {
            val initialTopPadding = paddingTop
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                view.updatePadding(top = initialTopPadding + insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
                insets
            }
            ViewCompat.requestApplyInsets(this)
            setNavigationOnClickListener { finish() }
        }
        clearButton.setOnClickListener { confirmClear() }
        renderEntries()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) renderEntries()
    }

    private fun renderEntries() {
        val entries = historyRepository.entries()
        adapter.replace(entries)
        historyList.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        emptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        clearButton.isEnabled = entries.isNotEmpty()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("예약 이력 전체 삭제")
            .setMessage("저장된 예약 제출 이력을 모두 삭제할까요?")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("전체 삭제") { _, _ ->
                historyRepository.clear()
                renderEntries()
            }
            .show()
    }

    private class ReservationHistoryAdapter(
        private var entries: List<ReservationHistoryEntry>,
    ) : BaseAdapter() {
        override fun getCount(): Int = entries.size

        override fun getItem(position: Int): ReservationHistoryEntry = entries[position]

        override fun getItemId(position: Int): Long = entries[position].attemptedAt.toEpochMilli()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_reservation_history, parent, false)
            val entry = getItem(position)
            val status = view.findViewById<TextView>(R.id.history_status)
            status.text = when (entry.status) {
                ReservationHistoryStatus.SUCCESS -> "등록 성공"
                ReservationHistoryStatus.FAILURE -> "등록 실패"
            }
            status.setBackgroundResource(
                if (entry.status == ReservationHistoryStatus.SUCCESS) R.drawable.bg_status_success
                else R.drawable.bg_status_error,
            )
            status.setTextColor(
                view.context.getColor(
                    if (entry.status == ReservationHistoryStatus.SUCCESS) R.color.status_success_on
                    else R.color.status_error_on,
                ),
            )
            view.findViewById<TextView>(R.id.history_period).text =
                "${entry.startDate} ~ ${entry.endDate}"
            view.findViewById<TextView>(R.id.history_vehicle).text = entry.maskedVehicleNumber
            view.findViewById<TextView>(R.id.history_attempted_at).text =
                entry.attemptedAt.atZone(ZoneId.systemDefault()).format(ATTEMPTED_AT_FORMAT)
            return view
        }

        fun replace(newEntries: List<ReservationHistoryEntry>) {
            entries = newEntries
            notifyDataSetChanged()
        }

        private companion object {
            val ATTEMPTED_AT_FORMAT: DateTimeFormatter =
                DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.KOREA)
        }
    }
}
