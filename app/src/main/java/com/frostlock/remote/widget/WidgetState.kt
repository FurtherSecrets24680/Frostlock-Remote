package com.frostlock.remote.widget

data class WidgetState(
    val configured: Boolean = false,
    val checking: Boolean = false,
    val active: Boolean = false,
    val busy: Boolean = false,
    val mode: String? = null,
    val remainingSeconds: Long = 0L,
    val remoteReleaseAllowed: Boolean = true,
    val error: String? = null,
) {
    val buttonLabel: String
        get() = when {
            !configured -> "Set up"
            busy -> "Working"
            active -> "Unfreeze"
            else -> "Freeze"
        }

    val statusLine: String
        get() = when {
            !configured -> "Tap to choose a PC"
            busy -> "Talking to your PC…"
            error != null -> error
            checking -> "Checking your PC…"
            active && !remoteReleaseAllowed -> "Release is blocked by this PC"
            active && remainingSeconds > 0 -> "${mode ?: "Freeze"} mode, ${formatRemaining(remainingSeconds)}"
            active -> "${mode ?: "Freeze"} mode is active"
            else -> "Tap to freeze"
        }
}

internal fun useCompactWidgetLayout(minWidthDp: Int, minHeightDp: Int): Boolean =
    minWidthDp in 1..149 || minHeightDp in 1..149

private fun formatRemaining(seconds: Long): String {
    val minutes = (seconds + 59) / 60
    return when {
        minutes >= 60 && minutes % 60 == 0L -> "${minutes / 60} hr left"
        minutes >= 60 -> "${minutes / 60} hr ${minutes % 60} min left"
        else -> "$minutes min left"
    }
}
