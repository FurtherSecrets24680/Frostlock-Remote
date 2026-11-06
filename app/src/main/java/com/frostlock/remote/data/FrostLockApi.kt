package com.frostlock.remote.data

import android.annotation.SuppressLint
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.X509TrustManager

class FrostLockApi(private val signer: DeviceSigner) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    @SuppressLint("CustomX509TrustManager")
    private fun client(profile: PcProfile, pairingApproval: Boolean = false): OkHttpClient {
        val expected = profile.certificateSha256.lowercase()
        val trustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                require(chain.isNotEmpty()) { "PC did not provide a certificate." }
                chain[0].checkValidity()
                val actual = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded)
                    .joinToString("") { "%02x".format(it) }
                require(actual == expected) { "PC certificate changed. Pair this PC again to verify its identity." }
            }
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustManager)
            .hostnameVerifier(HostnameVerifier { _, session ->
                runCatching {
                    val certificate = session.peerCertificates.firstOrNull() as? X509Certificate
                        ?: return@HostnameVerifier false
                    certificate.checkValidity()
                    val actual = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
                        .joinToString("") { "%02x".format(it) }
                    actual == expected
                }.getOrDefault(false)
            })
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(if (pairingApproval) 100 else 8, TimeUnit.SECONDS)
            .callTimeout(if (pairingApproval) 100 else 15, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
    }

    suspend fun pair(invite: PairingInvite, deviceName: String, alias: String): PcProfile {
        val publicKey = signer.create(alias)
        try {
            val body = JSONObject().put("token", invite.token).put("deviceName", deviceName)
                .put("publicKey", publicKey).toString()
            val request = Request.Builder().url("${invite.endpoint}/v1/pair")
                .post(body.toRequestBody(jsonType)).build()
            client(PcProfile("", "", invite.endpoint, invite.certificateSha256, alias), pairingApproval = true)
                .newCall(request).execute().use { response ->
                    val envelope = JSONObject(response.body?.string().orEmpty())
                    if (!response.isSuccessful || !envelope.optBoolean("ok")) {
                        error(safeError(envelope, response.code))
                    }
                    val data = envelope.optJSONObject("data") ?: error("Pairing response was incomplete.")
                    return PcProfile(
                        id = data.getString("deviceId"), name = invite.pcName,
                        endpoint = invite.endpoint, certificateSha256 = invite.certificateSha256,
                        keyAlias = alias,
                    )
                }
        } catch (error: Throwable) {
            signer.delete(alias)
            throw error
        }
    }

    suspend fun status(profile: PcProfile): FreezeSnapshot = authenticated(profile, "GET", "/v1/status", null)
        .let { FreezeSnapshot.fromJson(it) }

    suspend fun lockSettings(profile: PcProfile): PcLockSettings =
        PcLockSettings.fromJson(authenticated(profile, "GET", "/v1/lock-settings", null))

    suspend fun updateLockSettings(profile: PcProfile, settings: PcLockSettings): PcLockSettings =
        PcLockSettings.fromJson(authenticated(profile, "POST", "/v1/lock-settings", settings.toJson()))

    suspend fun freeze(profile: PcProfile, seconds: Int, mode: String? = null): FreezeSnapshot {
        val body = JSONObject().put("durationSeconds", seconds).put("idempotencyKey", java.util.UUID.randomUUID().toString())
        if (mode != null) body.put("mode", mode)
        return FreezeSnapshot.fromJson(authenticated(profile, "POST", "/v1/freeze", body))
    }

    suspend fun unlock(profile: PcProfile): FreezeSnapshot {
        val body = JSONObject().put("idempotencyKey", java.util.UUID.randomUUID().toString())
        return FreezeSnapshot.fromJson(authenticated(profile, "POST", "/v1/unlock", body))
    }

    suspend fun taskList(profile: PcProfile): WindowsTaskSnapshot =
        WindowsTaskSnapshot.fromJson(authenticated(profile, "GET", "/v1/tasks", null))

    suspend fun endTask(profile: PcProfile, pid: Int, force: Boolean = false) {
        authenticated(profile, "POST", "/v1/tasks/kill", JSONObject().put("pid", pid).put("force", force))
    }

    suspend fun startTask(
        profile: PcProfile,
        path: String,
        arguments: List<String> = emptyList(),
        workingDirectory: String? = null,
    ): StartedTaskSummary {
        val body = JSONObject().put("path", path)
        if (arguments.isNotEmpty()) body.put("arguments", JSONArray(arguments))
        if (!workingDirectory.isNullOrBlank()) body.put("workingDirectory", workingDirectory)
        val data = authenticated(profile, "POST", "/v1/tasks/start", body)
        return StartedTaskSummary(
            pid = data.optInt("pid"),
            name = data.optString("name", path.substringAfterLast('\\')),
        )
    }

    suspend fun runCommand(profile: PcProfile, command: String): StartedTaskSummary {
        val trimmed = command.trim()
        require(trimmed.isNotEmpty()) { "Enter a command to run." }
        return startTask(
            profile = profile,
            path = "C:\\Windows\\System32\\cmd.exe",
            arguments = listOf("/c", trimmed),
        )
    }

    suspend fun taskIcons(profile: PcProfile, keys: Collection<String>): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        keys.asSequence().filter(String::isNotBlank).distinct().chunked(24).forEach { batch ->
            val data = authenticated(
                profile,
                "POST",
                "/v1/tasks/icons",
                JSONObject().put("keys", JSONArray(batch)),
            )
            val icons = data.optJSONArray("icons") ?: return@forEach
            for (index in 0 until icons.length()) {
                val icon = icons.optJSONObject(index) ?: continue
                val key = icon.optString("key")
                val png = icon.optString("pngBase64")
                if (key.isNotBlank() && png.isNotBlank()) {
                    runCatching { Base64.decode(png, Base64.NO_WRAP) }
                        .onSuccess { result[key] = it }
                }
            }
        }
        return result
    }

    private suspend fun authenticated(profile: PcProfile, method: String, path: String, body: JSONObject?): JSONObject {
        val http = client(profile)
        val challengeReq = Request.Builder().url("${profile.endpoint}/v1/session/challenge")
            .post(JSONObject().put("deviceId", profile.id).toString().toRequestBody(jsonType)).build()
        val challenge = http.newCall(challengeReq).execute().use { response ->
            val envelope = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful || !envelope.optBoolean("ok")) error(safeError(envelope, response.code))
            envelope.getJSONObject("data")
        }
        val nonce = challenge.getString("nonce")
        val timestamp = challenge.getLong("serverTime")
        val requestId = java.util.UUID.randomUUID().toString()
        val bytes = body?.toString()?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val bodyHash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val canonical = "$method\n$path\n$nonce\n$timestamp\n$requestId\n$bodyHash".toByteArray(Charsets.UTF_8)
        val signature = signer.sign(profile.keyAlias, canonical)
        val requestBody = if (method == "GET") null else bytes.toRequestBody(jsonType)
        val request = Request.Builder().url(profile.endpoint + path)
            .header("X-FrostLock-Device", profile.id)
            .header("X-FrostLock-Nonce", nonce)
            .header("X-FrostLock-Timestamp", timestamp.toString())
            .header("X-FrostLock-Request-Id", requestId)
            .header("X-FrostLock-Signature", signature)
            .method(method, requestBody)
            .build()
        return http.newCall(request).execute().use { response ->
            val envelope = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful || !envelope.optBoolean("ok")) error(safeError(envelope, response.code))
            envelope.optJSONObject("data") ?: error("PC returned an incomplete response.")
        }
    }

    private fun safeError(envelope: JSONObject, status: Int): String =
        envelope.optJSONObject("error")?.optString("message")?.takeIf(String::isNotBlank)
            ?: when (status) {
                401, 403 -> "This phone is not authorized. Check paired devices in FrostLock on the PC."
                else -> "FrostLock returned an error (HTTP $status)."
            }
}
