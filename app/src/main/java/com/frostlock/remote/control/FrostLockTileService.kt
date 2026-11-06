package com.frostlock.remote.control

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.frostlock.remote.MainActivity
import com.frostlock.remote.R
import com.frostlock.remote.data.PcProfile
import kotlinx.coroutines.launch

class FrostLockTileService : TileService() {

    @Volatile
    private var working = false

    override fun onStartListening() {
        super.onStartListening()
        updateOnMain()
        RemoteControl.scope.launch { refreshFromPc() }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateOnMain()
        RemoteControl.scope.launch { refreshFromPc() }
    }

    override fun onClick() {
        super.onClick()
        if (working) return

        val store = ControlStore(this)
        val profileId = store.lastProfileId()
        if (profileId == null) {
            launchNow(Intent(this, MainActivity::class.java))
            return
        }

        if (store.active(profileId)) {
            launchNow(ConfirmUnlockActivity.intent(this, profileId))
            return
        }

        working = true
        updateOnMain(busy = true)
        RemoteControl.scope.launch {
            try {
                freeze(profileId)
            } finally {
                working = false
                updateOnMain()
            }
        }
    }

    private suspend fun freeze(profileId: String) {
        val context = this@FrostLockTileService
        val profile = RemoteControl.profile(context, profileId)
        if (profile == null) {
            ControlStore(context).clearLastProfile()
            RemoteControl.toast(context, "That PC is no longer paired.")
            return
        }
        try {
            val snapshot = RemoteControl.freeze(profile)
            RemoteControl.reflect(context, profile, snapshot)
            RemoteControl.toast(
                context,
                if (snapshot.isActive) "Freeze started on ${profile.name}."
                else "${profile.name} did not report a freeze.",
            )
        } catch (error: Throwable) {
            RemoteControl.toast(context, RemoteControl.shortError(error))
        }
    }

    private suspend fun refreshFromPc() {
        val context = this@FrostLockTileService
        val profile = resolveProfile(context)
        if (profile == null) {
            updateOnMain()
            return
        }
        try {
            RemoteControl.reflect(context, profile, RemoteControl.status(profile), refreshTile = false)
        } catch (_: Throwable) {
        }
        updateOnMain()
    }

    /** The PC the tile last showed, or the app's default, or the most recently used one. */
    private suspend fun resolveProfile(context: Context): PcProfile? {
        val store = ControlStore(context)
        store.lastProfileId()?.let { id ->
            RemoteControl.profile(context, id)?.let { return it }
        }
        val fallback = RemoteControl.defaultProfile(context) ?: return null
        store.setLastProfileId(fallback.id)
        return fallback
    }

    private fun updateOnMain(busy: Boolean = false) {
        Handler(Looper.getMainLooper()).post { updateTile(busy) }
    }

    private fun updateTile(busy: Boolean) {
        val tile = qsTile ?: return
        val store = ControlStore(this)
        val targetId = store.lastProfileId()
        val configured = targetId != null
        val active = configured && targetId != null && store.active(targetId)

        tile.state = when {
            busy -> Tile.STATE_UNAVAILABLE
            active -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = when {
            busy -> getString(R.string.tile_working)
            !configured -> getString(R.string.tile_setup)
            active -> getString(R.string.tile_unlock)
            else -> getString(R.string.tile_freeze)
        }
        tile.icon = Icon.createWithResource(
            this,
            if (active) R.drawable.ic_tile_unlock else R.drawable.ic_tile_lock,
        )
        tile.contentDescription = tile.label
        tile.updateTile()
    }

    /** Must be called from [onClick] while the tile service is still bound. */
    private fun launchNow(intent: Intent) {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        /** Asks the system to call [onStartListening] again so the tile redraws. */
        fun requestRefresh(context: Context) {
            runCatching {
                TileService.requestListeningState(
                    context,
                    ComponentName(context, FrostLockTileService::class.java),
                )
            }
        }
    }
}