package com.my.parking.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.my.parking.domain.RemainingTimeResult
import com.my.parking.domain.RemainingTimeText
import org.json.JSONObject
import org.json.JSONTokener

/** Reads the already-rendered remaining-time label from the authenticated WebView session. */
class RemainingTimeActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var completed = false
    private val timeoutHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this).apply {
            alpha = 0f
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    when {
                        url.contains("/login") -> finishWith(RemainingTimeResult.LoginRequired)
                        url.startsWith(RESERVATION_URL) -> readRemainingTime()
                    }
                }
            }
            loadUrl(RESERVATION_URL)
        }
        setContentView(webView)
        timeoutHandler.postDelayed({ finishWith(RemainingTimeResult.NetworkError) }, REQUEST_TIMEOUT_MILLIS)
    }

    private fun readRemainingTime() {
        if (completed) return
        webView.evaluateJavascript(READ_REMAINING_TIME_SCRIPT) { rawResult ->
            val result = runCatching { resultFromScript(rawResult) }
                .getOrDefault(RemainingTimeResult.Unavailable)
            finishWith(result)
        }
    }

    private fun resultFromScript(rawResult: String): RemainingTimeResult {
        val response = JSONObject(JSONTokener(rawResult).nextValue() as String)
        return when (response.optString("status")) {
            "login_required" -> RemainingTimeResult.LoginRequired
            "available" -> RemainingTimeText.fromDisplayedText(response.optString("value"))
            else -> RemainingTimeResult.Unavailable
        }
    }

    private fun finishWith(result: RemainingTimeResult) {
        if (completed) return
        completed = true
        timeoutHandler.removeCallbacksAndMessages(null)
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(EXTRA_STATUS, when (result) {
                is RemainingTimeResult.Available -> STATUS_AVAILABLE
                RemainingTimeResult.LoginRequired -> STATUS_LOGIN_REQUIRED
                RemainingTimeResult.Unavailable -> STATUS_UNAVAILABLE
                RemainingTimeResult.NetworkError -> STATUS_NETWORK_ERROR
            }).apply {
                if (result is RemainingTimeResult.Available) putExtra(EXTRA_VALUE, result.displayText)
            },
        )
        finish()
    }

    override fun onDestroy() {
        timeoutHandler.removeCallbacksAndMessages(null)
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val RESERVATION_URL = "http://srym2.iptime.org/nxpmsc/book-car"
        private const val REQUEST_TIMEOUT_MILLIS = 15_000L
        const val EXTRA_STATUS = "remaining_time_status"
        const val EXTRA_VALUE = "remaining_time_value"
        const val STATUS_AVAILABLE = "available"
        const val STATUS_LOGIN_REQUIRED = "login_required"
        const val STATUS_UNAVAILABLE = "unavailable"
        const val STATUS_NETWORK_ERROR = "network_error"

        private val READ_REMAINING_TIME_SCRIPT = """
            (function () {
              if (document.querySelector('input[type=password]')) {
                return JSON.stringify({ status: 'login_required' });
              }
              const item = document.querySelector(
                'li.nav-item[data-original-title="충전 잔여시간"] .nav-link-text'
              );
              return JSON.stringify(
                item ? { status: 'available', value: item.textContent.trim() } : { status: 'unavailable' }
              );
            })();
        """.trimIndent()

        fun intentFor(context: Context): Intent = Intent(context, RemainingTimeActivity::class.java)
    }
}
