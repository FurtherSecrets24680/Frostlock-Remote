package com.frostlock.remote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.frostlock.remote.R
import com.frostlock.remote.control.ConfirmUnlockActivity
import com.frostlock.remote.control.RemoteControl

/**
 * Home-screen widget mirroring the big freeze button in the app: one circle that
 * freezes the bound PC, or releases it while a freeze is active.
 *
 * One widget controls one paired PC. The launcher runs [WidgetConfigureActivity]
 * when the widget is added so the PC can be picked. Unlocks are handed to
 * [ConfirmUnlockActivity] so the widget cannot bypass the phone's confirmation.
 */
class FrostLockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        // Draw the cached state immediately, then refresh from the PC in the background.
        appWidgetIds.forEach { widgetId ->
            renderWidget(context, manager, widgetId, cachedState(context, widgetId))
        }
        refresh(context, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = WidgetStore(context)
        appWidgetIds.forEach(store::unbind)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TAP) {
            super.onReceive(context, intent)
            return
        }

        val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        // A broadcast receiver gets roughly ten seconds; keep the handle alive until
        // the network round trips finish, then let the system reclaim the process.
        val pending = goAsync()
        RemoteControl.scope.launch {
            try {
                handleTap(context, widgetId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_TAP = "com.frostlock.remote.widget.TAP"
        private const val EXTRA_WIDGET_ID = "com.frostlock.remote.widget.WIDGET_ID"

        /** Re-reads the PC behind each widget and redraws it. Safe to call from the app UI. */
        fun refreshAll(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, FrostLockWidget::class.java))
            refresh(app, ids)
        }

        internal fun refresh(context: Context, widgetIds: IntArray) {
            if (widgetIds.isEmpty()) return
            val app = context.applicationContext
            RemoteControl.scope.launch { widgetIds.forEach { refreshWidget(app, it) } }
        }

        internal fun tapIntent(context: Context, widgetId: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            widgetId * 4 + 1,
            Intent(context, FrostLockWidget::class.java).apply {
                action = ACTION_TAP
                putExtra(EXTRA_WIDGET_ID, widgetId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

private suspend fun handleTap(context: Context, widgetId: Int) {
    val store = WidgetStore(context)
    val manager = AppWidgetManager.getInstance(context)
    val profileId = store.profileId(widgetId) ?: run {
        openConfigure(context, widgetId)
        return
    }

    val current = store.state(widgetId)
    if (current.busy) return

    // Offline or never checked: a tap means "try again", not "guess and act".
    if (!current.known || current.error != null) {
        store.saveState(widgetId, current.copy(busy = true))
        renderWidget(context, manager, widgetId, current.copy(busy = true).toWidgetState(true))
        refreshWidget(context, widgetId)
        return
    }

    val profile = RemoteControl.profile(context, profileId) ?: run {
        // The PC was removed on the phone; make the widget point at setup again.
        store.unbind(widgetId)
        renderWidget(context, manager, widgetId, WidgetState(configured = false))
        return
    }

    if (current.active) {
        // Unlocking is gated: the shared screen asks the phone to confirm first.
        if (!current.remoteReleaseAllowed) {
            RemoteControl.toast(context, "Release is blocked by this PC.")
            return
        }
        store.saveState(widgetId, current.copy(error = null))
        openUnlock(context, profile.id)
        return
    }

    store.saveState(widgetId, current.copy(busy = true, error = null))
    renderWidget(context, manager, widgetId, current.copy(busy = true, error = null).toWidgetState(true))

    try {
        val snapshot = RemoteControl.freeze(profile)
        RemoteControl.reflect(context, profile, snapshot)

        val next = StoredState(
            known = true,
            active = snapshot.isActive,
            busy = false,
            mode = snapshot.mode,
            remainingSeconds = snapshot.remainingSeconds ?: 0L,
            remoteReleaseAllowed = snapshot.remoteReleaseAllowed,
            error = null,
        )
        store.saveState(widgetId, next)
        renderWidget(context, manager, widgetId, next.toWidgetState(true))
        RemoteControl.toast(
            context,
            if (snapshot.isActive) "Freeze confirmed by ${profile.name}."
            else "${profile.name} did not report an active freeze.",
        )
    } catch (error: Throwable) {
        val next = current.copy(busy = false, known = true, error = RemoteControl.shortError(error))
        store.saveState(widgetId, next)
        renderWidget(context, manager, widgetId, next.toWidgetState(true))
        RemoteControl.toast(context, RemoteControl.shortError(error))
    }
}

private suspend fun refreshWidget(context: Context, widgetId: Int) {
    val manager = AppWidgetManager.getInstance(context)
    val store = WidgetStore(context)
    val profileId = store.profileId(widgetId) ?: run {
        renderWidget(context, manager, widgetId, WidgetState(configured = false))
        return
    }

    val profile = RemoteControl.profile(context, profileId) ?: run {
        store.unbind(widgetId)
        renderWidget(context, manager, widgetId, WidgetState(configured = false))
        return
    }

    val previous = store.state(widgetId)
    if (!previous.known) {
        renderWidget(context, manager, widgetId, previous.toWidgetState(true).copy(checking = true))
    }

    try {
        val snapshot = RemoteControl.status(profile)
        RemoteControl.reflect(context, profile, snapshot)
        val next = StoredState(
            known = true,
            active = snapshot.isActive,
            busy = false,
            mode = snapshot.mode,
            remainingSeconds = snapshot.remainingSeconds ?: 0L,
            remoteReleaseAllowed = snapshot.remoteReleaseAllowed,
            error = null,
        )
        store.saveState(widgetId, next)
        renderWidget(context, manager, widgetId, next.toWidgetState(true))
    } catch (error: Throwable) {
        val next = previous.copy(known = true, busy = false, error = RemoteControl.shortError(error))
        store.saveState(widgetId, next)
        renderWidget(context, manager, widgetId, next.toWidgetState(true))
    }
}

private fun cachedState(context: Context, widgetId: Int): WidgetState {
    val store = WidgetStore(context)
    return store.state(widgetId).toWidgetState(store.profileId(widgetId) != null)
}

private fun renderWidget(context: Context, manager: AppWidgetManager, widgetId: Int, state: WidgetState) {
    val views = RemoteViews(context.packageName, R.layout.widget_frostlock)
    val active = state.configured && state.active

    views.setInt(R.id.widget_circle, "setBackgroundResource", when {
        !state.configured -> R.drawable.widget_circle_setup
        active -> R.drawable.widget_circle_active
        state.error != null || state.checking || state.busy -> R.drawable.widget_circle_neutral
        else -> R.drawable.widget_circle_primary
    })

    views.setImageViewResource(R.id.widget_icon, when {
        active -> R.drawable.ic_widget_unlock_error
        !state.configured -> R.drawable.ic_widget_lock_primary
        state.error != null || state.checking || state.busy -> R.drawable.ic_widget_lock_muted
        else -> R.drawable.ic_widget_lock_white
    })

    views.setTextViewText(R.id.widget_label, state.buttonLabel)
    views.setTextColor(R.id.widget_label, when {
        active -> COLOR_ACTIVE
        !state.configured -> COLOR_PRIMARY
        state.error != null || state.checking || state.busy -> COLOR_MUTED
        else -> COLOR_TEXT
    })

    views.setTextViewText(R.id.widget_status, state.statusLine)
    views.setTextColor(R.id.widget_status, COLOR_MUTED)

    views.setViewVisibility(R.id.widget_progress, if (state.busy) View.VISIBLE else View.GONE)
    views.setViewVisibility(R.id.widget_icon, if (state.busy) View.GONE else View.VISIBLE)

    val click = if (state.configured) {
        FrostLockWidget.tapIntent(context, widgetId)
    } else {
        configureIntent(context, widgetId)
    }
    views.setOnClickPendingIntent(R.id.widget_circle, click)
    views.setOnClickPendingIntent(R.id.widget_root, click)

    manager.updateAppWidget(widgetId, views)
}

private fun configureIntent(context: Context, widgetId: Int): PendingIntent = PendingIntent.getActivity(
    context,
    widgetId * 4 + 2,
    Intent(context, WidgetConfigureActivity::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    },
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

private fun openConfigure(context: Context, widgetId: Int) {
    context.startActivity(
        Intent(context, WidgetConfigureActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}

private fun openUnlock(context: Context, profileId: String) {
    context.startActivity(
        ConfirmUnlockActivity.intent(context, profileId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private val COLOR_PRIMARY = 0xFF147DAB.toInt()
private val COLOR_ACTIVE = 0xFFB3261E.toInt()
private val COLOR_TEXT = 0xFF101E2E.toInt()
private val COLOR_MUTED = 0xFF607181.toInt()
