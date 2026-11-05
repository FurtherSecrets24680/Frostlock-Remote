package com.frostlock.remote.data

import org.json.JSONObject

data class PcProfile(
    val id: String,
    val name: String,
    val endpoint: String,
    val certificateSha256: String,
    val keyAlias: String,
    val pairedAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long? = null,
)

data class PairingInvite(
    val endpoint: String,
    val certificateSha256: String,
    val token: String,
    val expiresAt: Long,
    val pcName: String,
) {
    companion object {
        fun parse(raw: String, nowSeconds: Long = System.currentTimeMillis() / 1000): PairingInvite {
            val json = JSONObject(PairingCodeCrypto.decrypt(raw))
            require(json.optInt("v") == 1) { "This pairing code uses an unsupported version." }
            val endpoint = json.optString("e", "").trimEnd('/')
            val fingerprint = json.optString("f", "").lowercase().replace(":", "")
            val uri = java.net.URI(endpoint)
            require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()) {
                "Pairing code must contain a valid HTTPS address."
            }
            require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
                "Pairing address contains unsupported URL parts."
            }
            require(fingerprint.matches(Regex("[0-9a-f]{64}"))) { "Certificate fingerprint is invalid." }
            val token = json.getString("t")
            require(token.length in 16..512) { "Pairing token is invalid." }
            val expiry = json.getLong("x")
            require(expiry > nowSeconds) { "This pairing code has expired. Create a new one on the PC." }
            require(expiry <= nowSeconds + 600) { "Pairing code expiry is outside the allowed window." }
            return PairingInvite(endpoint, fingerprint, token, expiry, json.optString("n", "FrostLock PC"))
        }
    }
}

data class FreezeSnapshot(
    val phase: String,
    val mode: String?,
    val remainingSeconds: Long?,
    val remoteReleaseAllowed: Boolean,
    val observedAt: String?,
) {
    val isActive: Boolean get() = phase.equals("frozen", true) || phase.equals("arming", true)

    companion object {
        fun fromJson(json: JSONObject): FreezeSnapshot = FreezeSnapshot(
            phase = json.optString("phase", "unknown"),
            mode = json.optString("mode").takeIf(String::isNotBlank),
            remainingSeconds = if (json.isNull("remainingSeconds")) null else json.optLong("remainingSeconds"),
            remoteReleaseAllowed = json.optBoolean("remoteReleaseAllowed", false),
            observedAt = json.optString("observedAt").takeIf(String::isNotBlank),
        )
    }
}

data class PcLockSettings(
    val mode: String,
    val durationSeconds: Int,
    val armSeconds: Int,
    val safetyMaxSeconds: Int,
    val blockAllKeys: Boolean,
    val blockMouse: Boolean,
    val blockSystemKeys: Boolean,
    val lockScreenStyle: String,
    val lockScreenOnAllDisplays: Boolean,
    val showCountdownOnOverlay: Boolean,
    val showClockOnOverlay: Boolean,
    val lockScreenMessage: String,
    val keepDisplayAwake: Boolean,
    val playSound: Boolean,
    val lockWindowsOnRelease: Boolean,
    val hasPin: Boolean,
    val mobilePinReleaseAllowed: Boolean,
) {
    fun toJson() = JSONObject().apply {
        put("mode", mode)
        put("durationSeconds", durationSeconds)
        put("armSeconds", armSeconds)
        put("safetyMaxSeconds", safetyMaxSeconds)
        put("blockAllKeys", blockAllKeys)
        put("blockMouse", blockMouse)
        put("blockSystemKeys", blockSystemKeys)
        put("lockScreenStyle", lockScreenStyle)
        put("lockScreenOnAllDisplays", lockScreenOnAllDisplays)
        put("showCountdownOnOverlay", showCountdownOnOverlay)
        put("showClockOnOverlay", showClockOnOverlay)
        put("lockScreenMessage", lockScreenMessage)
        put("keepDisplayAwake", keepDisplayAwake)
        put("playSound", playSound)
        put("lockWindowsOnRelease", lockWindowsOnRelease)
    }

    companion object {
        fun fromJson(json: JSONObject) = PcLockSettings(
            mode = json.optString("mode", "Gentle"),
            durationSeconds = json.optInt("durationSeconds", 900),
            armSeconds = json.optInt("armSeconds", 3),
            safetyMaxSeconds = json.optInt("safetyMaxSeconds", 0),
            blockAllKeys = json.optBoolean("blockAllKeys", true),
            blockMouse = json.optBoolean("blockMouse", true),
            blockSystemKeys = json.optBoolean("blockSystemKeys", true),
            lockScreenStyle = json.optString("lockScreenStyle", "Solid"),
            lockScreenOnAllDisplays = json.optBoolean("lockScreenOnAllDisplays", true),
            showCountdownOnOverlay = json.optBoolean("showCountdownOnOverlay", true),
            showClockOnOverlay = json.optBoolean("showClockOnOverlay", true),
            lockScreenMessage = json.optString("lockScreenMessage", ""),
            keepDisplayAwake = json.optBoolean("keepDisplayAwake", false),
            playSound = json.optBoolean("playSound", true),
            lockWindowsOnRelease = json.optBoolean("lockWindowsOnRelease", false),
            hasPin = json.optBoolean("hasPin", false),
            mobilePinReleaseAllowed = json.optBoolean("mobilePinReleaseAllowed", false),
        )
    }
}

data class WindowsTask(
    val pid: Int,
    val name: String,
    val displayName: String,
    val publisher: String?,
    val version: String?,
    val path: String?,
    val iconKey: String?,
    val cpuPercent: Double,
    val workingSetBytes: Long,
    val privateBytes: Long,
    val diskReadBytes: Long,
    val diskWriteBytes: Long,
    val diskReadBytesPerSec: Double,
    val diskWriteBytesPerSec: Double,
    val connectionCount: Int,
    val remoteEndpoints: List<String>,
    val threadCount: Int,
    val sessionId: Int,
    val startedAtMs: Long,
    val mainWindowTitle: String?,
    val hasWindow: Boolean,
    val elevated: Boolean?,
    val protected: Boolean,
    val protectedReason: String?,
)

data class WindowsTaskSnapshot(
    val sampledAtMs: Long,
    val sampleMilliseconds: Int,
    val machineName: String,
    val userName: String,
    val controllerElevated: Boolean,
    val totalCpuPercent: Double,
    val totalMemoryBytes: Long,
    val usedMemoryBytes: Long,
    val networkReceiveBytesPerSec: Long,
    val networkSendBytesPerSec: Long,
    val processCount: Int,
    val appCount: Int,
    val connectionCount: Int,
    val processes: List<WindowsTask>,
) {
    companion object {
        fun fromJson(json: JSONObject): WindowsTaskSnapshot {
            val array = json.optJSONArray("processes")
            val processes = buildList {
                if (array != null) {
                    for (index in 0 until array.length()) {
                        array.optJSONObject(index)?.let { add(parseWindowsTask(it)) }
                    }
                }
            }
            return WindowsTaskSnapshot(
                sampledAtMs = json.optLong("sampledAtMs"),
                sampleMilliseconds = json.optInt("sampleMilliseconds", 350),
                machineName = json.optString("machineName", "Windows PC"),
                userName = json.optString("userName", ""),
                controllerElevated = json.optBoolean("controllerElevated", false),
                totalCpuPercent = json.optDouble("totalCpuPercent", 0.0),
                totalMemoryBytes = json.optLong("totalMemoryBytes"),
                usedMemoryBytes = json.optLong("usedMemoryBytes"),
                networkReceiveBytesPerSec = json.optLong("networkReceiveBytesPerSec"),
                networkSendBytesPerSec = json.optLong("networkSendBytesPerSec"),
                processCount = json.optInt("processCount", processes.size),
                appCount = json.optInt("appCount"),
                connectionCount = json.optInt("connectionCount"),
                processes = processes,
            )
        }
    }
}

data class StartedTaskSummary(val pid: Int, val name: String)

private fun parseWindowsTask(json: JSONObject): WindowsTask {
    val endpoints = json.optJSONArray("remoteEndpoints")
    val remoteEndpoints = buildList {
        if (endpoints != null) {
            for (index in 0 until endpoints.length()) {
                endpoints.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }
    val elevatedValue = if (json.has("elevated") && !json.isNull("elevated")) json.optBoolean("elevated") else null
    return WindowsTask(
        pid = json.optInt("pid"),
        name = json.optString("name", "process"),
        displayName = json.optString("displayName", json.optString("name", "Process")),
        publisher = json.optString("publisher").takeIf(String::isNotBlank),
        version = json.optString("version").takeIf(String::isNotBlank),
        path = json.optString("path").takeIf(String::isNotBlank),
        iconKey = json.optString("iconKey").takeIf(String::isNotBlank),
        cpuPercent = json.optDouble("cpuPercent", 0.0),
        workingSetBytes = json.optLong("workingSetBytes"),
        privateBytes = json.optLong("privateBytes"),
        diskReadBytes = json.optLong("diskReadBytes"),
        diskWriteBytes = json.optLong("diskWriteBytes"),
        diskReadBytesPerSec = json.optDouble("diskReadBytesPerSec", 0.0),
        diskWriteBytesPerSec = json.optDouble("diskWriteBytesPerSec", 0.0),
        connectionCount = json.optInt("connectionCount"),
        remoteEndpoints = remoteEndpoints,
        threadCount = json.optInt("threadCount"),
        sessionId = json.optInt("sessionId", -1),
        startedAtMs = json.optLong("startedAtMs"),
        mainWindowTitle = json.optString("mainWindowTitle").takeIf(String::isNotBlank),
        hasWindow = json.optBoolean("hasWindow", false),
        elevated = elevatedValue,
        protected = json.optBoolean("protected", false),
        protectedReason = json.optString("protectedReason").takeIf(String::isNotBlank),
    )
}

internal fun JSONObject.toProfile(): PcProfile = PcProfile(
    id = getString("id"), name = getString("name"), endpoint = getString("endpoint"),
    certificateSha256 = getString("certificateSha256"), keyAlias = getString("keyAlias"),
    pairedAt = optLong("pairedAt"), lastSeenAt = if (isNull("lastSeenAt")) null else optLong("lastSeenAt"),
)

internal fun PcProfile.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("endpoint", endpoint)
    put("certificateSha256", certificateSha256); put("keyAlias", keyAlias)
    put("pairedAt", pairedAt); put("lastSeenAt", lastSeenAt)
}
