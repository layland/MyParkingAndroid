package com.my.parking.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.my.parking.MyParkingApplication
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** The bridge is available only to the configured HTTPS origin and the bundled app assets. */
class HybridActivity : AppCompatActivity() {
    private val container get() = (application as MyParkingApplication).appContainer
    private lateinit var webView: WebView
    private lateinit var main: MainViewModel
    private lateinit var family: FamilySharingViewModel
    private lateinit var timeLoader: RemainingTimeLoader
    private var currentOrigin = LOCAL_ORIGIN
    private var isLocal = true

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        main=ViewModelProvider(this,object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T = MainViewModel(container.vehiclePreferences) as T
        })[MainViewModel::class.java]
        family=ViewModelProvider(this,object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T = FamilySharingViewModel(container.familySharingRepository) as T
        })[FamilySharingViewModel::class.java]
        timeLoader=RemainingTimeLoader(this,main::renderRemainingTime)
        webView=WebView(this)
        setContentView(webView)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val padding=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(padding.left,padding.top,padding.right,padding.bottom)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            startActivity(Intent(this,MainActivity::class.java)); finish(); return
        }
        webView.settings.apply {
            javaScriptEnabled=true
            domStorageEnabled=false
            allowFileAccess=false
            allowContentAccess=false
            mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        val assets=WebViewAssetLoader.Builder().addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(this)).build()
        webView.webViewClient=object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView,request: WebResourceRequest): WebResourceResponse? = assets.shouldInterceptRequest(request.url)
            override fun shouldOverrideUrlLoading(view: WebView,request: WebResourceRequest): Boolean {
                val origin="${request.url.scheme}://${request.url.host}"
                if(request.isForMainFrame && origin==currentOrigin) return false
                // Never transfer the bridge to links, subframes, or the parking site's login page.
                if(request.isForMainFrame && request.url.scheme == "https") startActivity(Intent(Intent.ACTION_VIEW,request.url))
                return true
            }
            override fun onReceivedHttpError(view: WebView,request: WebResourceRequest,error: WebResourceResponse) {
                if(request.isForMainFrame && !isLocal) { isLocal=true; currentOrigin=LOCAL_ORIGIN; view.loadUrl(LOCAL_URL) }
            }
            override fun onReceivedError(view: WebView,request: WebResourceRequest,error: WebResourceError) {
                if(request.isForMainFrame && !isLocal) { isLocal=true; currentOrigin=LOCAL_ORIGIN; view.loadUrl(LOCAL_URL) }
            }
        }
        installBridge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { main.uiState.collect { render() } }
                launch { main.messages.collect { message -> emit("notice",JSONObject().put("text",message.text).put("kind",message.kind.name)) } }
            }
        }
        onBackPressedDispatcher.addCallback(this,object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("window.ParkingApp && window.ParkingApp.back()") { handled -> if(handled != "true") finish() }
            }
        })
        if(main.beginRemainingTimeRefresh()) timeLoader.refresh()
    }

    private fun installBridge() {
        val remote=runCatching { container.familySharingRepository.origin() }.getOrDefault("")
        currentOrigin=remote.ifBlank { LOCAL_ORIGIN }; isLocal=remote.isBlank()
        WebViewCompat.removeWebMessageListener(webView,"NativeParking")
        WebViewCompat.addWebMessageListener(webView,"NativeParking",setOf(LOCAL_ORIGIN,currentOrigin)) { _,message,sourceOrigin,isMainFrame,reply ->
            if(!isMainFrame || sourceOrigin.toString().trimEnd('/') != currentOrigin) return@addWebMessageListener
            val raw=message.data ?: return@addWebMessageListener
            if(raw.length>16384) return@addWebMessageListener
            val request=runCatching { JSONObject(raw) }.getOrNull() ?: return@addWebMessageListener
            val id=request.optString("id"); if(id.length !in 1..80) return@addWebMessageListener
            val action=request.optString("action"); val data=request.optJSONObject("data") ?: JSONObject()
            val answer: (JSONObject)->Unit = { result -> if(!isDestroyed) reply.postMessage(result.put("id",id).toString()) }
            runCatching {
                when(action) {
                    "state" -> answer(JSONObject().put("ok",true).put("data",state()))
                    "updateDraft" -> { main.updateVehicleNumber(data.getString("vehicleNumber")); main.updateVisitorName(data.optString("visitorName")); answer(ok()); render() }
                    "startDate" -> { main.selectStartDate(LocalDate.parse(data.getString("date"))); answer(ok()); render() }
                    "endDate" -> { main.selectEndDate(LocalDate.parse(data.getString("date"))); answer(ok()); render() }
                    "quickDate" -> { main.selectQuickPeriodEnding(LocalDate.now().plusDays(data.getInt("offset").toLong())); answer(ok()); render() }
                    "saveVehicle" -> { main.saveVehicle(); answer(ok()); render() }
                    "selectVehicle" -> { main.selectVehicle(data.getString("number")); answer(ok()); render() }
                    "deleteVehicle" -> confirm("저장 차량 삭제","현재 선택한 차량을 저장 목록에서 삭제할까요?",cancelled={ answer(JSONObject().put("ok",false).put("error","취소했습니다.")) }) { main.deleteCurrentVehicle(); answer(ok()); render() }
                    "register" -> { main.reservationDraftOrNull()?.let { startActivity(ReservationWebActivity.intentFor(this,it)) }; answer(ok()) }
                    "site" -> { startActivity(Intent(this,ReservationWebActivity::class.java)); answer(ok()) }
                    "refreshTime" -> { if(main.beginRemainingTimeRefresh()) timeLoader.refresh(); answer(ok()) }
                    "configure" -> { answer(ok()); configureServer() }
                    "nativeScreen" -> { startActivity(Intent(this,MainActivity::class.java)); answer(ok()) }
                    "familyImport" -> confirm("기존 내역 공유","이 기기의 기존 예약 내역을 가족에게 공유할까요?",cancelled={ answer(JSONObject().put("ok",false).put("error","취소했습니다.")) }) {
                        container.reservationHistoryRepository.entries().forEach { container.familySharingRepository.enqueue(it,if(it.status.name == "SUCCESS") "SUCCESS" else "UNKNOWN") }
                        family.execute("familySync",data,answer)
                    }
                    "shareInvite" -> {
                        val code=data.getString("code"); require(Regex("[a-f0-9]{32}").matches(code))
                        val text="우리집 주차 예약 가족 초대\n서버 주소: ${container.familySharingRepository.origin()}\n초대 코드: $code\n앱 가족 설정에서 연결하세요. 24시간 안에 한 번 사용할 수 있습니다."
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,text) },"배우자에게 초대 보내기")); answer(ok())
                    }
                    "familyLeave" -> confirm("가족 연결 해제","이 기기의 공유 접근을 해제합니다. 마지막 기기라면 서버의 가족 내역도 삭제됩니다. 계속할까요?",cancelled={ answer(JSONObject().put("ok",false).put("error","취소했습니다.")) }) { family.execute(action,data,answer) }
                    "familyRevoke" -> confirm("다른 기기 연결 해제","선택한 기기가 가족 내역을 볼 수 없게 됩니다. 계속할까요?",cancelled={ answer(JSONObject().put("ok",false).put("error","취소했습니다.")) }) { family.execute(action,data,answer) }
                    else -> if(action.startsWith("family")) family.execute(action,data,answer) else answer(JSONObject().put("ok",false).put("error","앱 업데이트가 필요한 기능입니다."))
                }
            }.onFailure { answer(JSONObject().put("ok",false).put("error",it.message ?: "요청을 처리할 수 없습니다.")) }
        }
        webView.loadUrl(if(isLocal) LOCAL_URL else "$currentOrigin/")
    }
    private fun configureServer() {
        val input=EditText(this).apply { setText(container.familySharingRepository.origin()); hint="https://myparking-family.example.workers.dev"; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI }
        AlertDialog.Builder(this).setTitle("웹 UI · 공유 서버 주소").setMessage("본인이 배포한 HTTPS 주소를 입력하세요. 이 주소의 웹 화면이 앱의 예약 기능에 접근합니다.")
            .setView(input).setNegativeButton("취소",null).setPositiveButton("연결") { _,_ ->
                runCatching { container.familySharingRepository.configure(input.text.toString()) }.onSuccess { installBridge() }
                    .onFailure { AlertDialog.Builder(this).setMessage(it.message).setPositiveButton("확인",null).show() }
            }.show()
    }
    private fun confirm(title: String,message: String,cancelled: ()->Unit,confirmed: ()->Unit) = AlertDialog.Builder(this).setTitle(title).setMessage(message).setOnCancelListener { cancelled() }.setNegativeButton("취소") { _,_ -> cancelled() }.setPositiveButton("확인") { _,_->confirmed() }.show()
    private fun state(): JSONObject {
        val s=main.uiState.value
        return JSONObject().put("protocol",1).put("vehicleNumber",s.vehicleNumber).put("visitorName",s.visitorName)
            .put("startDate",s.startDate.value.toString()).put("endDate",s.endDate.value.toString()).put("today",LocalDate.now().toString())
            .put("remainingTime",s.remainingTime).put("refreshing",s.isRefreshingRemainingTime)
            .put("origin",runCatching { container.familySharingRepository.origin() }.getOrDefault(""))
            .put("savedVehicles",JSONArray().apply { s.savedVehicles.forEach { put(JSONObject().put("number",it.number).put("label",it.number.dropLast(4)+"****").put("name",it.visitorName)) } })
            .put("localHistory",JSONArray().apply { container.reservationHistoryRepository.entries().forEach { e -> put(JSONObject().put("vehicle",e.vehicleNumber.dropLast(4)+"****").put("startDate",e.startDate.toString()).put("endDate",e.endDate.toString()).put("attemptedAt",e.attemptedAt.toString()).put("status",e.status.name)) } })
    }
    private fun ok()=JSONObject().put("ok",true)
    private fun render() { if(::webView.isInitialized) emit("render",state()) }
    private fun emit(method: String,data: JSONObject) { webView.evaluateJavascript("window.ParkingApp && window.ParkingApp.$method($data)",null) }
    override fun onResume() { super.onResume(); if(::webView.isInitialized && ::main.isInitialized) emit("resume",state()) }
    override fun onDestroy() { if(::timeLoader.isInitialized) timeLoader.cancel(); if(::webView.isInitialized) webView.destroy(); super.onDestroy() }
    companion object { private const val LOCAL_ORIGIN="https://appassets.androidplatform.net"; private const val LOCAL_URL="$LOCAL_ORIGIN/assets/hybrid/index.html" }
}
