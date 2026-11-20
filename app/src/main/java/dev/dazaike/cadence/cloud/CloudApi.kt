package dev.dazaike.cadence.cloud

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** Outcome of one HTTP call. Network/transport problems and server errors are values, not exceptions. */
sealed interface CloudResult<out T> {
    data class Ok<T>(val value: T) : CloudResult<T>

    data class Failure(val kind: Kind, val status: Int, val message: String) : CloudResult<Nothing>

    enum class Kind { NETWORK, UNAUTHORIZED, FORBIDDEN, CONFLICT, RATE_LIMITED, TOO_LARGE, BAD_REQUEST, SERVER, OTHER }
}

data class CloudSession(val token: String, val email: String)

data class SettingsBlob(val json: JSONObject?, val updatedAt: Long?)

/**
 * Minimal client for the Cadence cloud REST API (see the API contract). Plain
 * [HttpURLConnection] so no networking dependency is added. Every call runs on [Dispatchers.IO].
 * Secrets (password, token) are only ever placed in request bodies/headers, never in URLs or logs.
 */
class CloudApi {

    suspend fun login(baseUrl: String, email: String, password: String): CloudResult<CloudSession> =
        authenticate("$baseUrl/api/auth/login", email, password)

    suspend fun register(baseUrl: String, email: String, password: String): CloudResult<CloudSession> =
        authenticate("$baseUrl/api/auth/register", email, password)

    suspend fun logout(baseUrl: String, token: String): CloudResult<Unit> =
        call("POST", "$baseUrl/api/auth/logout", token, null, SHORT_READ_MS).map { }

    /** Uploads one already-built `{src, tz, metrics}` payload. Returns the number of points the server upserted. */
    suspend fun syncSamples(baseUrl: String, token: String, body: String): CloudResult<Int> =
        call("POST", "$baseUrl/api/sync/samples", token, body, SYNC_READ_MS).map { it?.optInt("upserted", 0) ?: 0 }

    suspend fun getSettings(baseUrl: String, token: String, namespace: String): CloudResult<SettingsBlob> =
        call("GET", "$baseUrl/api/settings/$namespace", token, null, SHORT_READ_MS).map { resp ->
            SettingsBlob(
                json = resp?.optJSONObject("json"),
                updatedAt = resp?.takeIf { it.has("updatedAt") && !it.isNull("updatedAt") }?.getLong("updatedAt"),
            )
        }

    suspend fun putSettings(
        baseUrl: String,
        token: String,
        namespace: String,
        json: JSONObject,
        baseUpdatedAt: Long?,
    ): CloudResult<Unit> {
        val body = JSONObject().put("json", json).apply { if (baseUpdatedAt != null) put("baseUpdatedAt", baseUpdatedAt) }
        return call("PUT", "$baseUrl/api/settings/$namespace", token, body.toString(), SHORT_READ_MS).map { }
    }

    private suspend fun authenticate(url: String, email: String, password: String): CloudResult<CloudSession> {
        val body = JSONObject().put("email", email).put("password", password).put("label", "Android")
        return call("POST", url, null, body.toString(), SHORT_READ_MS).map { resp ->
            val token = resp?.optString("token").orEmpty()
            if (token.isEmpty()) return CloudResult.Failure(CloudResult.Kind.SERVER, 200, "Server returned no session token.")
            CloudSession(token, resp?.optJSONObject("user")?.optString("email")?.takeIf { it.isNotBlank() } ?: email)
        }
    }

    private suspend fun call(
        method: String,
        url: String,
        token: String?,
        body: String?,
        readTimeoutMs: Int,
    ): CloudResult<JSONObject?> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_MS
                readTimeout = readTimeoutMs
                useCaches = false
                // A redirect would silently drop the Authorization header or downgrade the scheme.
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                if (token != null) setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { readLimited(it) }.orEmpty()
            if (status in 200..299) {
                CloudResult.Ok(parseObject(text))
            } else {
                failure(status, text)
            }
        } catch (e: IOException) {
            CloudResult.Failure(CloudResult.Kind.NETWORK, 0, e.message ?: e.javaClass.simpleName)
        } catch (e: IllegalArgumentException) {
            CloudResult.Failure(CloudResult.Kind.BAD_REQUEST, 0, "Invalid server address.")
        } finally {
            conn?.disconnect()
        }
    }

    private fun failure(status: Int, text: String): CloudResult.Failure {
        val json = parseObject(text)
        val message = json?.optString("message")?.takeIf { it.isNotBlank() }
            ?: json?.optString("error")?.takeIf { it.isNotBlank() }
            ?: "HTTP $status"
        val kind = when (status) {
            400 -> CloudResult.Kind.BAD_REQUEST
            401 -> CloudResult.Kind.UNAUTHORIZED
            403 -> CloudResult.Kind.FORBIDDEN
            409 -> CloudResult.Kind.CONFLICT
            413 -> CloudResult.Kind.TOO_LARGE
            429 -> CloudResult.Kind.RATE_LIMITED
            in 500..599 -> CloudResult.Kind.SERVER
            else -> CloudResult.Kind.OTHER
        }
        return CloudResult.Failure(kind, status, message)
    }

    private fun parseObject(text: String): JSONObject? =
        if (text.isBlank()) null else try { JSONObject(text) } catch (_: JSONException) { null }

    private fun readLimited(input: java.io.InputStream): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_RESPONSE_BYTES) throw IOException("Response too large")
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private inline fun <T, R> CloudResult<T>.map(transform: (T) -> R): CloudResult<R> = when (this) {
        is CloudResult.Ok -> CloudResult.Ok(transform(value))
        is CloudResult.Failure -> this
    }

    companion object {
        private const val CONNECT_MS = 15_000
        private const val SHORT_READ_MS = 20_000
        private const val SYNC_READ_MS = 60_000
        private const val MAX_RESPONSE_BYTES = 1_000_000

        /**
         * Normalises a user-typed server address to `scheme://host[:port]` (no path, no trailing
         * slash). https is mandatory except for loopback / the emulator host, where plain http is
         * allowed for local development. Returns null when the address is unusable.
         */
        fun normalizeServerUrl(input: String): String? {
            val raw = input.trim().trimEnd('/')
            if (raw.isEmpty()) return null
            val uri = try { URI(raw) } catch (_: Exception) { return null }
            val scheme = uri.scheme?.lowercase() ?: return null
            val host = uri.host?.lowercase() ?: return null
            if (uri.userInfo != null) return null
            val local = host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
            if (scheme != "https" && !(scheme == "http" && local)) return null
            val port = if (uri.port == -1) "" else ":${uri.port}"
            return "$scheme://$host$port"
        }
    }
}
