package com.my.parking.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.my.parking.domain.RemainingTimeResult
import com.my.parking.domain.RemainingTimeText
import org.json.JSONObject
import org.json.JSONTokener

/** Loads the official site's displayed remaining time without navigating away from the current screen. */
class RemainingTimeLoader(
    context: Context,
    private val onResult: (RemainingTimeResult) -> Unit,
) {
    private val context = context
    private val handler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var completed = false
    private var readAttempts = 0

    @SuppressLint("SetJavaScriptEnabled")
    fun refresh() {
        cancel()
        completed = false
        readAttempts = 0
        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    when {
                        url.contains("/login") -> complete(RemainingTimeResult.LoginRequired)
                        url.startsWith(RESERVATION_URL) -> readRemainingTime()
                    }
                }
            }
            loadUrl(RESERVATION_URL)
        }
        handler.postDelayed({ complete(RemainingTimeResult.NetworkError) }, REQUEST_TIMEOUT_MILLIS)
    }

    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        completed = true
    }

    private fun readRemainingTime() {
        if (completed) return
        webView?.evaluateJavascript(READ_REMAINING_TIME_SCRIPT) { rawResult ->
            val result = runCatching { resultFromScript(rawResult) }
                .getOrDefault(RemainingTimeResult.Unavailable)
            if (result == RemainingTimeResult.Unavailable && readAttempts < MAX_READ_ATTEMPTS) {
                readAttempts++
                handler.postDelayed(::readRemainingTime, RETRY_DELAY_MILLIS)
            } else {
                complete(result)
            }
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

    private fun complete(result: RemainingTimeResult) {
        if (completed) return
        completed = true
        handler.removeCallbacksAndMessages(null)
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        onResult(result)
    }

    private companion object {
        const val RESERVATION_URL = "http://srym2.iptime.org/nxpmsc/book-car"
        const val REQUEST_TIMEOUT_MILLIS = 15_000L
        const val RETRY_DELAY_MILLIS = 500L
        const val MAX_READ_ATTEMPTS = 10

        val READ_REMAINING_TIME_SCRIPT = """
            (function () {
              if (document.querySelector('input[type=password]')) {
                return JSON.stringify({ status: 'login_required' });
              }
              const item = Array.from(document.querySelectorAll('.nav-link-text')).find(element =>
                /^\\d{1,4}\\s*\\uC2DC\\uAC04\\s*\\d{1,2}\\s*\\uBD84\\s*\\uB0A8\\uC74C$/.test(element.textContent.trim())
              );
              return JSON.stringify(
                item ? { status: 'available', value: item.textContent.trim() } : { status: 'unavailable' }
              );
            })();
        """.trimIndent()
    }
}
