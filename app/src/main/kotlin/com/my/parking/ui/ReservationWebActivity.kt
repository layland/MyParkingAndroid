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
import com.my.parking.R
import com.my.parking.data.ReservationPostContract
import com.my.parking.data.ReservationSubmissionGate
import com.my.parking.data.ReservationSubmissionResult
import com.my.parking.domain.ReservationDate
import com.my.parking.domain.ReservationDraft
import com.my.parking.domain.ReservationPeriod
import com.my.parking.domain.VehicleNumber
import com.my.parking.domain.VisitorName
import java.time.LocalDate
import org.json.JSONObject

/** Submits through the official site's authenticated WebView session without reading cookies. */
class ReservationWebActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var draft: ReservationDraft
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

    private fun readDraft(): ReservationDraft {
        val vehicle = requireNotNull(intent.getStringExtra(EXTRA_VEHICLE_NUMBER))
        val visitorName = requireNotNull(intent.getStringExtra(EXTRA_VISITOR_NAME))
        val startDate = requireNotNull(intent.getStringExtra(EXTRA_START_DATE))
        val endDate = requireNotNull(intent.getStringExtra(EXTRA_END_DATE))
        return ReservationDraft(
            visitorName = VisitorName.of(visitorName),
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
        webView.evaluateJavascript(submissionScript(payload), null)
    }

    private fun handleSubmissionResult(encodedResult: String) {
        if (resultHandled) return
        resultHandled = true
        val result = runCatching {
            val responseText = String(Base64.decode(encodedResult, Base64.NO_WRAP), Charsets.UTF_8)
            val response = JSONObject(responseText)
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
          const report = value => {
            const encoded = btoa(unescape(encodeURIComponent(JSON.stringify(value))));
            document.title = '$RESULT_TITLE_PREFIX' + encoded;
          };
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
            report({
              httpStatus: response.status,
              result: body && body.result,
              message: body && body.message,
            });
          } catch (_) {
            report({ networkError: true });
          }
        })();
    """.trimIndent()

    companion object {
        private const val RESERVATION_URL = "http://srym2.iptime.org/nxpmsc/book-car"
        private const val EXTRA_START_DATE = "reservation_start_date"
        private const val EXTRA_END_DATE = "reservation_end_date"
        private const val EXTRA_VEHICLE_NUMBER = "vehicle_number"
        private const val EXTRA_VISITOR_NAME = "visitor_name"
        private const val RESULT_TITLE_PREFIX = "myparking-reservation-result:"

        fun intentFor(context: Context, draft: ReservationDraft): Intent =
            Intent(context, ReservationWebActivity::class.java)
                .putExtra(EXTRA_START_DATE, draft.reservationPeriod.startDate.value.toString())
                .putExtra(EXTRA_END_DATE, draft.reservationPeriod.endDate.value.toString())
                .putExtra(EXTRA_VEHICLE_NUMBER, draft.vehicleNumber.value)
                .putExtra(EXTRA_VISITOR_NAME, draft.visitorName.value)
    }
}
