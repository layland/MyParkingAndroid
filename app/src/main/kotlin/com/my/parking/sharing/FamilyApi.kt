package com.my.parking.sharing

import java.net.HttpURLConnection
import java.net.URI
import org.json.JSONObject

object FamilyOrigin {
    fun validate(value: String): String {
        val uri = URI(value.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.query == null && uri.fragment == null && (uri.path.isNullOrEmpty() || uri.path == "/") &&
            (uri.port == -1 || uri.port == 443)) { "HTTPS 서버 주소만 입력해 주세요. 예: https://myparking-family.example.workers.dev" }
        return "https://${uri.host.lowercase()}"
    }
}

class FamilyApiException(val status: Int, message: String) : IllegalStateException(message)

/** No redirects: credentials must never travel to another host. Called only on the IO dispatcher. */
class FamilyApi {
    fun request(origin: String, path: String, method: String = "GET", token: String? = null, body: JSONObject? = null): JSONObject {
        val connection = URI(FamilyOrigin.validate(origin) + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val result = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (status !in 200..299) throw FamilyApiException(status,result.optString("error").ifBlank { "공유 서버에 연결할 수 없습니다. ($status)" })
            return result
        } finally { connection.disconnect() }
    }
}
