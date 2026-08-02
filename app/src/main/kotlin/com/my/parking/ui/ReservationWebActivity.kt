package com.my.parking.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.my.parking.R
import com.my.parking.data.ReservationPostContract
import com.my.parking.data.ReservationSubmissionGate
import com.my.parking.data.ReservationSubmissionResult
import com.my.parking.domain.ReservationDate
import com.my.parking.domain.ReservationDraft
import com.my.parking.domain.ReservationPeriod
import com.my.parking.domain.VehicleNumber
import java.time.LocalDate
import org.json.JSONObject
import org.json.JSONTokener

/** Submits through the official site's authenticated WebView session without reading cookies. */
class ReservationWebActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var draft: ReservationDraft
    private var confirmationShown = false
    private val submissionGate = ReservationSubmissionGate()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reservation_web)

        draft = readDraft()
        webView = findViewById<WebView>(R.id.reservation_web_view).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (!confirmationShown && !submissionGate.hasStarted && url.contains("/book-car")) {
                        confirmAfterLoginIfReady()
                    }
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

    private fun readDraft(): ReservationDraft {
        val vehicle = requireNotNull(intent.getStringExtra(EXTRA_VEHICLE_NUMBER))
        val startDate = requireNotNull(intent.getStringExtra(EXTRA_START_DATE))
        val endDate = requireNotNull(intent.getStringExtra(EXTRA_END_DATE))
        return ReservationDraft(
            vehicleNumber = VehicleNumber.of(vehicle),
            reservationPeriod = ReservationPeriod.of(
                ReservationDate.of(LocalDate.parse(startDate)),
                ReservationDate.of(LocalDate.parse(endDate)),
            ),
        )
    }

    private fun confirmAfterLoginIfReady() {
        webView.evaluateJavascript("(function(){ return !document.querySelector('input[type=password]'); })()") { result ->
            if (result == "true" && !confirmationShown && !submissionGate.hasStarted && !isFinishing) {
                confirmationShown = true
                AlertDialog.Builder(this)
                    .setTitle(R.string.reservation_confirmation_title)
                    .setMessage(R.string.reservation_confirmation_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.confirm) { _, _ -> submitReservation() }
                    .show()
            }
        }
    }

    private fun submitReservation() {
        if (!submissionGate.begin()) return
        showMessage(getString(R.string.reservation_submitting_message))

        val payload = JSONObject(ReservationPostContract.parameters(draft)).toString()
        webView.evaluateJavascript(submissionScript(payload)) { rawResult ->
            handleSubmissionResult(rawResult)
        }
    }

    private fun handleSubmissionResult(rawResult: String) {
        val result = runCatching {
            val encodedResult = JSONTokener(rawResult).nextValue() as String
            val response = JSONObject(encodedResult)
            if (response.optBoolean("networkError", false)) {
                ReservationSubmissionResult.Failure(getString(R.string.reservation_result_unknown))
            } else {
                ReservationSubmissionResult.from(
                    httpStatus = response.optInt("httpStatus", 0),
                    result = response.takeIf { it.has("result") }?.optString("result"),
                    message = response.takeIf { it.has("message") }?.optString("message"),
                )
            }
        }.getOrElse {
            ReservationSubmissionResult.Failure(getString(R.string.reservation_result_unknown))
        }

        when (result) {
            is ReservationSubmissionResult.Success -> showMessage(
                result.message.ifBlank { getString(R.string.reservation_success_message) },
            )
            is ReservationSubmissionResult.Failure -> showMessage(result.message)
        }
    }

    private fun showMessage(message: String) {
        findViewById<View>(R.id.automation_notice).apply {
            visibility = View.VISIBLE
            (this as android.widget.TextView).text = message
        }
    }

    private fun submissionScript(payload: String): String = """
        (async function () {
          const payload = $payload;
          try {
            const response = await fetch('/nxpmsc/book-car/post', {
              method: 'POST',
              headers: {
                'Accept': 'application/json, text/javascript, */*; q=0.01',
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                'X-Requested-With': 'XMLHttpRequest',
              },
              credentials: 'include',
              body: new URLSearchParams(payload),
            });
            const text = await response.text();
            let body = null;
            try { body = JSON.parse(text); } catch (_) {}
            return JSON.stringify({
              httpStatus: response.status,
              result: body && body.result,
              message: body && body.message,
            });
          } catch (_) {
            return JSON.stringify({ networkError: true });
          }
        })();
    """.trimIndent()

    companion object {
        private const val RESERVATION_URL = "http://srym2.iptime.org/nxpmsc/book-car"
        private const val EXTRA_START_DATE = "reservation_start_date"
        private const val EXTRA_END_DATE = "reservation_end_date"
        private const val EXTRA_VEHICLE_NUMBER = "vehicle_number"

        fun intentFor(context: Context, draft: ReservationDraft): Intent =
            Intent(context, ReservationWebActivity::class.java)
                .putExtra(EXTRA_START_DATE, draft.reservationPeriod.startDate.value.toString())
                .putExtra(EXTRA_END_DATE, draft.reservationPeriod.endDate.value.toString())
                .putExtra(EXTRA_VEHICLE_NUMBER, draft.vehicleNumber.value)
    }
}
