package com.my.parking.sharing

import com.my.parking.domain.ReservationHistoryEntry
import com.my.parking.domain.ReservationHistoryRepository
import com.my.parking.domain.ReservationDraft
import com.my.parking.domain.ReservationHistoryStatus
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class FamilySharingRepository(private val store: SecureFamilyStore, private val api: FamilyApi = FamilyApi()) {
    init {
        // Existing family credentials remain bound to their original server.
        store.update { state ->
            if (!state.has("session") && state.optString("origin").isBlank()) {
                state.put("origin", FamilyServer.DEFAULT_ORIGIN)
            }
        }
    }
    private val mutex = Mutex()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    fun origin(): String = store.read().optString("origin")
    fun configure(origin: String) {
        val validated = FamilyOrigin.validate(origin)
        store.update { require(!it.has("session") || it.optString("origin") == validated) { "가족 연결을 해제한 후 서버를 바꿔 주세요." }; it.put("origin", validated) }
    }
    fun entryId(entry: ReservationHistoryEntry): String = MessageDigest.getInstance("SHA-256")
        .digest((entry.attemptedAt.toString()+"|"+entry.vehicleNumber+"|"+entry.startDate+"|"+entry.endDate).toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    fun enqueue(entry: ReservationHistoryEntry, status: String = entry.status.name, succeeded: Int? = null, total: Int? = null) {
        store.update { state ->
            if (!state.has("session")) return@update
            val id = entryId(entry)
            val count = total ?: (((java.time.temporal.ChronoUnit.DAYS.between(entry.startDate, entry.endDate)+1)+3)/4).toInt()
            val item = JSONObject().put("id",id).put("vehicle",entry.vehicleNumber)
                .put("startDate",entry.startDate.toString()).put("endDate",entry.endDate.toString()).put("attemptedAt",entry.attemptedAt.toString())
                .put("status",status).put("succeeded",succeeded ?: if(status == "SUCCESS") count else 0).put("total",count)
            val queue = state.optJSONArray("pending") ?: JSONArray()
            if ((0 until queue.length()).none { queue.getJSONObject(it).getString("id") == id }) queue.put(item)
            state.put("pending",queue)
        }
        scope.launch { runCatching { sync() } }
    }
    suspend fun connect(label: String, code: String? = null): JSONObject = withContext(Dispatchers.IO) { mutex.withLock {
        val state=store.read()
        require(!state.has("session")) { "이미 가족에 연결되어 있습니다." }
        require(state.optString("origin").isNotBlank()) { "먼저 공유 서버 주소를 설정해 주세요." }
        val session=api.request(state.getString("origin"),if(code == null) "/api/families" else "/api/join","POST",body=JSONObject().put("label",label).apply { code?.let { put("code",it.trim()) } })
        store.update { it.put("session",session).put("pending",JSONArray()); it.remove("cached"); it.remove("devices"); it.remove("lastSync") }
        snapshotLocked()
    } }
    suspend fun invite(): JSONObject = withContext(Dispatchers.IO) { mutex.withLock { authenticated("/api/invites","POST",JSONObject()) } }
    suspend fun revoke(id: String): JSONObject = withContext(Dispatchers.IO) { mutex.withLock {
        require(Regex("[a-fA-F0-9-]{36}").matches(id))
        authenticated("/api/devices/$id","DELETE"); snapshotLocked()
    } }
    suspend fun leave(): JSONObject = withContext(Dispatchers.IO) { mutex.withLock {
        // Server revocation must succeed before the local credential is discarded.
        try { authenticated("/api/leave","POST",JSONObject()) } catch(error: FamilyApiException) { if(error.status != 401) throw error }
        store.update { it.remove("session"); it.remove("pending"); it.remove("cached"); it.remove("devices"); it.remove("lastSync") }
        snapshotLocked()
    } }
    suspend fun sync(): JSONObject = withContext(Dispatchers.IO) { mutex.withLock { snapshotLocked() } }
    suspend fun more(): JSONObject = withContext(Dispatchers.IO) { mutex.withLock {
        val state=store.read(); val cached=state.optJSONObject("cached") ?: return@withLock snapshotLocked()
        val cursor=cached.optString("nextCursor").takeIf { !cached.isNull("nextCursor") && it.isNotBlank() } ?: return@withLock snapshotLocked()
        val remote=authenticated("/api/reservations?cursor="+java.net.URLEncoder.encode(cursor,"UTF-8"))
        val combined=cached.getJSONArray("entries")
        val entries=remote.getJSONArray("entries")
        for(i in 0 until entries.length()) combined.put(entries.getJSONObject(i))
        store.update { it.put("cached",remote.put("entries",combined)) }
        JSONObject().put("connected",true).put("entries",combined).put("devices",state.optJSONArray("devices") ?: JSONArray())
            .put("deviceId",state.getJSONObject("session").getString("deviceId")).put("label",state.getJSONObject("session").getString("label"))
            .put("pending",state.optJSONArray("pending")?.length() ?: 0)
            .put("pendingEntries",state.optJSONArray("pending") ?: JSONArray()).put("lastSync",state.optString("lastSync"))
            .put("hasMore",!remote.isNull("nextCursor"))
    } }
    private fun authenticated(path: String,method: String="GET",body: JSONObject?=null): JSONObject {
        val state=store.read(); val session=state.optJSONObject("session") ?: error("가족 연결이 필요합니다.")
        return api.request(state.getString("origin"),path,method,session.getString("token"),body)
    }
    private fun snapshotLocked(): JSONObject {
        var state=store.read(); val session=state.optJSONObject("session")
        val result=JSONObject().put("connected",session != null).put("origin",state.optString("origin"))
        if (session == null) return result.put("entries",JSONArray()).put("pending",0)
        result.put("deviceId",session.getString("deviceId")).put("label",session.getString("label"))
        try {
            val pending=state.optJSONArray("pending") ?: JSONArray()
            for(index in 0 until pending.length()) {
                val entry=pending.getJSONObject(index)
                authenticated("/api/reservations","PUT",entry)
                store.update { current ->
                    val queue=current.optJSONArray("pending") ?: JSONArray()
                    current.put("pending",JSONArray().apply { for(i in 0 until queue.length()) if(queue.getJSONObject(i).getString("id") != entry.getString("id")) put(queue.getJSONObject(i)) })
                }
            }
            val remote=authenticated("/api/reservations")
            val devices=authenticated("/api/devices").getJSONArray("devices")
            store.update { it.put("cached",remote).put("devices",devices).put("lastSync",java.time.Instant.now().toString()) }
            result.put("error",JSONObject.NULL)
        } catch(error: Exception) { result.put("error",error.message ?: "네트워크 연결을 확인해 주세요.") }
        state=store.read()
        return result.put("entries",state.optJSONObject("cached")?.optJSONArray("entries") ?: JSONArray())
            .put("devices",state.optJSONArray("devices") ?: JSONArray()).put("lastSync",state.optString("lastSync"))
            .put("pending",state.optJSONArray("pending")?.length() ?: 0)
            .put("pendingEntries",state.optJSONArray("pending") ?: JSONArray())
            .put("hasMore",state.optJSONObject("cached")?.opt("nextCursor") != null && !state.optJSONObject("cached")!!.isNull("nextCursor"))
    }
}

/** Recording a confirmed outcome queues sharing without sending a second parking reservation. */
class SharingHistoryRepository(private val local: ReservationHistoryRepository, private val sharing: FamilySharingRepository) : ReservationHistoryRepository by local {
    override fun record(draft: ReservationDraft, status: ReservationHistoryStatus) {
        local.record(draft,status)
        // The WebView supplies richer batch results after recording; that is the only enqueue path.
    }
    fun enqueueLatest(status: String,succeeded: Int,total: Int) {
        local.entries().firstOrNull()?.let { sharing.enqueue(it,status,succeeded,total) }
    }
}
