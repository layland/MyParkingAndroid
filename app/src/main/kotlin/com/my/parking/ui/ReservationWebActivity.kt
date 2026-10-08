package com.my.parking.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.my.parking.MyParkingApplication
import com.my.parking.R
import com.my.parking.data.ReservationPostContract
import com.my.parking.data.ReservationSubmissionGate
import com.my.parking.data.ReservationSubmissionResult
import com.my.parking.data.historyStatus
import com.my.parking.domain.ReservationDate
import com.my.parking.domain.ReservationDraft
import com.my.parking.domain.ReservationDurationOption
import com.my.parking.domain.ReservationPeriod
import com.my.parking.domain.VehicleNumber
import com.my.parking.domain.VisitorName
import java.time.LocalDate
import org.json.JSONObject

/** Submits through the official site's authenticated WebView session without reading cookies. */
class ReservationWebActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var draft: ReservationDraft? = null
    private var confirmationShown = false
    private var resultHandled = false
    private val submissionGate = ReservationSubmissionGate()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reservation_web)
        findViewById<MaterialToolbar>(R.id.reservation_toolbar).setNavigationOnClickListener {
            if (webView.canGoBack()) webView.goBack() else finish()
        }

        draft = readDraftOrNull()
        webView = findViewById<WebView>(R.id.reservation_web_view).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (!confirmationShown && !submissionGate.hasStarted && url.contains("/book-car")) {
                        confirmAfterLoginIfReady()
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, title: String?) {
                    super.onReceivedTitle(view, title)
                    title
                        ?.takeIf { it.startsWith(RESULT_TITLE_PREFIX) }
                        ?.removePrefix(RESULT_TITLE_PREFIX)
                        ?.let(::handleSubmissionResult)
                }
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            loadUrl(RESERVATION_URL)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun readDraftOrNull(): ReservationDraft? {
        val vehicle = intent.getStringExtra(EXTRA_VEHICLE_NUMBER) ?: return null
        val visitorName = intent.getStringExtra(EXTRA_VISITOR_NAME) ?: return null
        val durationOption = intent.getStringExtra(EXTRA_DURATION_OPTION)
        val startDate = intent.getStringExtra(EXTRA_START_DATE) ?: return null
        val endDate = intent.getStringExtra(EXTRA_END_DATE) ?: return null
        return ReservationDraft(
            visitorName = VisitorName.of(visitorName),
            vehicleNumber = VehicleNumber.of(vehicle),
            durationOption = ReservationDurationOption.fromStorageValue(durationOption),
            reservationPeriod = ReservationPeriod.of(
                ReservationDate.of(LocalDate.parse(startDate)),
                ReservationDate.of(LocalDate.parse(endDate)),
            ),
        )
    }

    private fun confirmAfterLoginIfReady() {
        val reservationDraft = draft ?: return
        webView.evaluateJavascript("(function(){ return !document.querySelector('input[type=password]'); })()") { result ->
            if (result == "true" && !confirmationShown && !submissionGate.hasStarted && !isFinishing) {
                confirmationShown = true
                AlertDialog.Builder(this)
                    .setTitle(R.string.reservation_confirmation_title)
                    .setMessage(
                        getString(R.string.reservation_confirmation_message) + "\n\n" +
                            "총 ${ReservationPeriod.splitForSubmission(reservationDraft.reservationPeriod).size}건으로 나누어 등록합니다.",
                    )
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.confirm) { _, _ -> submitReservation() }
                    .show()
            }
        }
    }

    private fun submitReservation() {
        val reservationDraft = draft ?: return
        if (!submissionGate.begin()) return
        showMessage(getString(R.string.reservation_submitting_message))

        val payloads = ReservationPeriod.splitForSubmission(reservationDraft.reservationPeriod)
            .map { period -> ReservationPostContract.parameters(reservationDraft, period) }
        webView.evaluateJavascript(submissionScript(org.json.JSONArray(payloads).toString()), null)
    }

    private fun handleSubmissionResult(encodedResult: String) {
        if (resultHandled) return
        val reservationDraft = draft ?: return
        resultHandled = true
        var sharedStatus = "UNKNOWN"
        var sharedSucceeded = 0
        var sharedTotal = ReservationPeriod.splitForSubmission(reservationDraft.reservationPeriod).size
        val result = runCatching {
            val responseText = String(Base64.decode(encodedResult, Base64.NO_WRAP), Charsets.UTF_8)
            val response = JSONObject(responseText)
            val responses = response.optJSONArray("responses")
            var knownFailure = false
            if (responses != null) {
                for (index in 0 until responses.length()) {
                    val item = responses.optJSONObject(index) ?: break
                    val outcome = ReservationSubmissionResult.from(item.optInt("httpStatus",0),item.takeIf { it.has("result") }?.optString("result"),item.takeIf { it.has("message") }?.optString("message"))
                    if (outcome is ReservationSubmissionResult.Success) sharedSucceeded++
                    else { knownFailure = !item.optBoolean("networkError") && item.has("result") && !item.isNull("result"); break }
                }
                sharedStatus = when {
                    sharedSucceeded == sharedTotal -> "SUCCESS"
                    sharedSucceeded > 0 -> "PARTIAL"
                    knownFailure -> "FAILURE"
                    else -> "UNKNOWN"
                }
            }
            batchResultMessage(response)
        }.getOrElse {
            ReservationSubmissionResult.Failure(getString(R.string.reservation_result_unknown))
        }
        (application as MyParkingApplication).appContainer.reservationHistoryRepository.record(
            draft = reservationDraft,
            status = result.historyStatus(),
        )

        var sharingWarning: String? = null
        runCatching {
            (application as MyParkingApplication).appContainer.reservationHistoryRepository
                .enqueueLatest(sharedStatus,sharedSucceeded,sharedTotal)
        }.onFailure { sharingWarning = "기기 내역은 저장했지만 공유 대기열 저장에 실패했습니다. 가족 설정에서 기존 내역 공유를 사용해 주세요." }

        when (result) {
            is ReservationSubmissionResult.Success -> showMessage(result.message.ifBlank { getString(R.string.reservation_success_message) })
            is ReservationSubmissionResult.Failure -> showMessage(result.message)
        }
        sharingWarning?.let { warning -> showMessage(findViewById<android.widget.TextView>(R.id.automation_notice).text.toString() + "\n" + warning) }
    }

    private fun batchResultMessage(response: JSONObject): ReservationSubmissionResult {
        val responses = response.optJSONArray("responses") ?: return ReservationSubmissionResult.Failure(
            getString(R.string.reservation_result_unknown),
        )
        var succeeded = 0
        var failureMessage: String? = null
        for (index in 0 until responses.length()) {
            val item = responses.optJSONObject(index) ?: break
            val outcome = if (item.optBoolean("networkError", false)) {
                ReservationSubmissionResult.Failure(getString(R.string.reservation_result_unknown))
            } else {
                ReservationSubmissionResult.from(
                    item.optInt("httpStatus", 0),
                    item.takeIf { it.has("result") }?.optString("result"),
                    item.takeIf { it.has("message") }?.optString("message"),
                )
            }
            if (outcome is ReservationSubmissionResult.Success) succeeded++ else {
                failureMessage = (outcome as ReservationSubmissionResult.Failure).message
                break
            }
        }
        val total = response.optInt("totalCount", responses.length())
        return if (failureMessage == null && succeeded == total) {
            ReservationSubmissionResult.Success("총 ${total}건의 예약이 등록되었습니다.")
        } else {
            ReservationSubmissionResult.Failure(
                "${total}건 중 ${succeeded}건 등록 후 중단되었습니다. ${failureMessage ?: getString(R.string.reservation_result_unknown)} 공식 사이트에서 먼저 확인해 주세요.",
            )
        }
    }

    private fun showMessage(message: String) {
        findViewById<View>(R.id.automation_notice).apply {
            visibility = View.VISIBLE
            (this as android.widget.TextView).text = message
        }
    }

    private fun submissionScript(payloads: String): String = """
        (async function () {
          const payloads = $payloads;
          const report = value => {
            const encoded = btoa(unescape(encodeURIComponent(JSON.stringify(value))));
            document.title = '$RESULT_TITLE_PREFIX' + encoded;
          };
          const responses = [];
          for (let index = 0; index < payloads.length; index += 1) {
            let item;
            try {
              const response = await fetch('/nxpmsc/book-car/post', {
              method: 'POST',
              headers: {
                'Accept': 'application/json, text/javascript, */*; q=0.01',
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                'X-Requested-With': 'XMLHttpRequest',
              },
              credentials: 'include',
                body: new URLSearchParams(payloads[index]),
              });
              const text = await response.text();
              let body = null;
              try { body = JSON.parse(text); } catch (_) {}
              item = {
                httpStatus: response.status,
                result: body && body.result,
                message: body && body.message,
              };
            } catch (_) {
              item = { networkError: true };
            }
            responses.push(item);
            if (!(item.httpStatus >= 200 && item.httpStatus < 300 && item.result === 'success')) break;
            if (index < payloads.length - 1) await new Promise(resolve => setTimeout(resolve, 1000));
          }
          report({ responses: responses, totalCount: payloads.length });
        })();
    """.trimIndent()

    companion object {
        private const val RESERVATION_URL = "http://srym2.iptime.org/nxpmsc/book-car"
        private const val EXTRA_START_DATE = "reservation_start_date"
        private const val EXTRA_END_DATE = "reservation_end_date"
        private const val EXTRA_VEHICLE_NUMBER = "vehicle_number"
        private const val EXTRA_VISITOR_NAME = "visitor_name"
        private const val EXTRA_DURATION_OPTION = "reservation_duration_option"
        private const val RESULT_TITLE_PREFIX = "myparking-reservation-result:"

        fun intentFor(context: Context, draft: ReservationDraft): Intent =
            Intent(context, ReservationWebActivity::class.java)
                .putExtra(EXTRA_START_DATE, draft.reservationPeriod.startDate.value.toString())
                .putExtra(EXTRA_END_DATE, draft.reservationPeriod.endDate.value.toString())
                .putExtra(EXTRA_VEHICLE_NUMBER, draft.vehicleNumber.value)
                .putExtra(EXTRA_VISITOR_NAME, draft.visitorName.value)
                .apply {
                    draft.durationOption?.let { putExtra(EXTRA_DURATION_OPTION, it.storageValue) }
                }
    }
}

