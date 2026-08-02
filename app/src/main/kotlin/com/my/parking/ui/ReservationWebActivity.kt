package com.my.parking.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.my.parking.R
import com.my.parking.domain.ReservationDraft
import org.json.JSONObject

/** Uses the official reservation page without accessing its credentials or cookies. */
class ReservationWebActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var shouldAutomateReservation = false
    private var hasSubmittedReservation = false
    private var automationAttempts = 0
    private var selectedDate = ""
    private var vehicleNumber = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reservation_web)

        selectedDate = intent.getStringExtra(EXTRA_RESERVATION_DATE).orEmpty()
        vehicleNumber = intent.getStringExtra(EXTRA_VEHICLE_NUMBER).orEmpty()
        shouldAutomateReservation = selectedDate.isNotBlank() && vehicleNumber.isNotBlank()

        webView = findViewById<WebView>(R.id.reservation_web_view).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (shouldAutomateReservation && !hasSubmittedReservation && url.contains("/book-car")) {
                        automationAttempts = 0
                        automateReservationWhenFormIsReady()
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

    private fun automateReservationWhenFormIsReady() {
        webView.evaluateJavascript(automationScript(selectedDate, vehicleNumber)) { result ->
            when (result) {
                "\"submitted\"" -> {
                    hasSubmittedReservation = true
                    findViewById<View>(R.id.automation_notice).visibility = View.VISIBLE
                }
                else -> retryAutomationIfNeeded()
            }
        }
    }

    private fun retryAutomationIfNeeded() {
        if (automationAttempts >= MAX_AUTOMATION_ATTEMPTS || hasSubmittedReservation) return
        automationAttempts += 1
        Handler(Looper.getMainLooper()).postDelayed(
            { automateReservationWhenFormIsReady() },
            AUTOMATION_RETRY_DELAY_MILLIS,
        )
    }

    /** Matches visible labels because the official site does not publish stable field IDs. */
    private fun automationScript(reservationDate: String, vehicle: String): String {
        val date = JSONObject.quote(reservationDate)
        val carNumber = JSONObject.quote(vehicle)
        return """
            (function () {
              const dateValue = $date;
              const vehicleValue = $carNumber;
              const normalise = value => (value || '').replace(/\s/g, '');
              const visible = element => !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
              const labelText = element => {
                const id = element.id ? document.querySelector('label[for="' + CSS.escape(element.id) + '"]') : null;
                const siblingLabel = element.previousElementSibling;
                const parentLabel = element.parentElement && element.parentElement.previousElementSibling;
                const formGroupLabel = element.closest('.form-group') && element.closest('.form-group').querySelector('label');
                return normalise([
                  id && id.textContent,
                  siblingLabel && siblingLabel.textContent,
                  parentLabel && parentLabel.textContent,
                  formGroupLabel && formGroupLabel.textContent,
                  element.getAttribute('aria-label'),
                  element.placeholder,
                  element.name,
                  element.id,
                ].filter(Boolean).join(' '));
              };
              const findField = words => Array.from(document.querySelectorAll('input, textarea'))
                .filter(element => visible(element) && !['hidden', 'password', 'submit', 'button'].includes(element.type))
                .find(element => words.some(word => labelText(element).includes(word)));
              const setValue = (element, value) => {
                const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                setter.call(element, value);
                ['input', 'change', 'blur'].forEach(type => element.dispatchEvent(new Event(type, { bubbles: true })));
              };
              const registrationMenu = Array.from(document.querySelectorAll('a, button, [role="button"]'))
                .filter(visible)
                .find(element => normalise(element.textContent).includes('차량방문예약등록'));
              const startDateField = findField(['기간시작일', '방문(예약)시작일시']);
              const endDateField = findField(['기간종료일', '방문(예약)종료일시']);
              const vehicleField = findField(['차량번호']);
              if (!startDateField || !endDateField || !vehicleField) {
                if (registrationMenu) {
                  registrationMenu.click();
                  return 'opening-registration-form';
                }
                return 'waiting-for-form';
              }
              setValue(startDateField, dateValue);
              setValue(endDateField, dateValue);
              setValue(vehicleField, vehicleValue);
              const submitButton = Array.from(document.querySelectorAll('button, input[type="submit"], input[type="button"], a'))
                .filter(visible)
                .find(element => normalise(element.textContent || element.value) === '등록');
              if (!submitButton) return 'waiting-for-submit';
              submitButton.click();
              return 'submitted';
            })();
        """.trimIndent()
    }

    companion object {
        private const val RESERVATION_URL = "http://srym2.iptime.org/nxpmsc/book-car"
        private const val EXTRA_RESERVATION_DATE = "reservation_date"
        private const val EXTRA_VEHICLE_NUMBER = "vehicle_number"
        private const val MAX_AUTOMATION_ATTEMPTS = 20
        private const val AUTOMATION_RETRY_DELAY_MILLIS = 500L

        fun intentFor(context: Context, draft: ReservationDraft): Intent =
            Intent(context, ReservationWebActivity::class.java)
                .putExtra(EXTRA_RESERVATION_DATE, draft.reservationDate.value.toString())
                .putExtra(EXTRA_VEHICLE_NUMBER, draft.vehicleNumber.value)
    }
}
