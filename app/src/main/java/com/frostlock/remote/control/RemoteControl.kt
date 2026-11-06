package com.frostlock.remote.control

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.frostlock.remote.data.DeviceSigner
import com.frostlock.remote.data.FreezeSnapshot
import com.frostlock.remote.data.FrostLockApi
import com.frostlock.remote.data.PcProfile
import com.frostlock.remote.data.ProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal object RemoteControl {

    /** Background work for surfaces with no lifecycle of their own. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val api = FrostLockApi(DeviceSigner())

    suspend fun profiles(context: Context): List<PcProfile> =
        withContext(Dispatchers.IO) { ProfileStore(context).all() }

    suspend fun profile(context: Context, profileId: String): PcProfile? =
        profiles(context).firstOrNull { it.id == profileId }

    /** The PC a shortcut should act on without asking. */
    suspend fun defaultProfile(context: Context): PcProfile? =
        ControlStore(context).resolve(profiles(context))

    suspend fun status(profile: PcProfile): FreezeSnapshot =
        withContext(Dispatchers.IO) { api.status(profile) }

    suspend fun freeze(profile: PcProfile): FreezeSnapshot = withContext(Dispatchers.IO) {
        val options = api.lockSettings(profile)
        api.freeze(profile, options.durationSeconds, options.mode)
    }

    suspend fun unlock(profile: PcProfile): FreezeSnapshot =
        withContext(Dispatchers.IO) { api.unlock(profile) }

    fun reflect(
        context: Context,
        profile: PcProfile,
        snapshot: FreezeSnapshot,
        refreshTile: Boolean = true,
    ) {
        val app = context.applicationContext
        ControlStore(app).setActive(profile.id, snapshot.isActive)
        if (snapshot.isActive) FreezeNotifier.show(app, profile, snapshot) else FreezeNotifier.cancel(app)
        if (refreshTile) FrostLockTileService.requestRefresh(app)
    }

    fun toast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun shortError(error: Throwable): String = when (error) {
        is SocketTimeoutException, is ConnectException, is NoRouteToHostException, is UnknownHostException ->
            "PC unreachable"
        is javax.net.ssl.SSLException -> "Secure connection failed"
        else -> error.message?.take(90)?.takeIf(String::isNotBlank) ?: "PC unreachable"
    }
}
