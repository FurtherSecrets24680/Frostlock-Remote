package com.frostlock.remote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frostlock.remote.data.AppPreferences
import com.frostlock.remote.data.PcProfile
import com.frostlock.remote.data.ThemeMode

@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    versionName: String,
    pcs: List<PcProfile>,
    onThemeChange: (ThemeMode) -> Unit,
    onFreezeDurationChange: (Int, Int?) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onRemovePc: (PcProfile) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var pendingRemoval by remember { mutableStateOf<PcProfile?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(containerColor = colors.background) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsHeader(onBack = onBack)
                Spacer(Modifier.height(10.dp))
            }

            item {
                SectionLabel("Appearance")
                SettingsCard {
                    Text("Theme", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.onSurface)
                    Text("Follow the device setting or choose a theme.", color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(8.dp))
                    ThemeDropdown(selected = preferences.theme, onSelect = onThemeChange)
                }
            }

            item {
                SectionLabel("Freeze controls")
                SettingsCard {
                    FreezeDurationPicker(preferences.freezeDurationSeconds, preferences.untilMinuteOfDay, onFreezeDurationChange)
                }
            }

            item {
                SectionLabel("Interaction")
                SettingsCard {
                    SettingSwitch(
                        title = "Haptic feedback",
                        description = "A short vibration after the PC confirms a freeze or release.",
                        checked = preferences.haptics,
                        onCheckedChange = onHapticsChange,
                    )
                }
            }

            item {
                SectionLabel("Paired PCs")
                SettingsCard {
                    if (pcs.isEmpty()) {
                        Text("No PCs paired yet.", color = colors.onSurfaceVariant, fontSize = 14.sp)
                    } else {
                        pcs.forEachIndexed { index, profile ->
                            if (index > 0) {
                                Spacer(Modifier.height(14.dp))
                                androidx.compose.material3.HorizontalDivider(color = colors.outlineVariant)
                                Spacer(Modifier.height(14.dp))
                            }
                            PairedPcRow(profile = profile, onRemove = { pendingRemoval = profile })
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Removing a PC deletes this phone's key for it. Revoke the phone on the PC as well if the device is lost.",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }

            item {
                SectionLabel("About")
                SettingsCard {
                    Text("FrostLock Remote", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = colors.onSurface)
                    Text("Version $versionName", color = colors.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(14.dp))
                    androidx.compose.material3.HorizontalDivider(color = colors.outlineVariant)
                    Spacer(Modifier.height(14.dp))
                    Text("A companion for your Windows PC", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Pair with FrostLock on your PC to check its status and control supported freezes from your phone.",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = { confirmReset = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                        modifier = Modifier.padding(horizontal = 0.dp),
                    ) { Text("Reset settings") }
                }
            }
        }
    }

    pendingRemoval?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove ${profile.name}?") },
            text = { Text("This removes the pairing and this phone's key from the app. It does not revoke the device on the PC.") },
            confirmButton = {
                TextButton(onClick = { pendingRemoval = null; onRemovePc(profile) }) {
                    Text("Remove", color = colors.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset settings?") },
            text = { Text("Theme, selected duration and haptics return to their defaults. Paired PCs are kept.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onReset() }) { Text("Reset", color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ThemeDropdown(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = "Theme selection" },
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                AnimatedContent(
                    targetState = selected.label,
                    transitionSpec = { (fadeIn(tween(140)) + androidx.compose.animation.slideInVertically(tween(160)) { it / 5 }) togetherWith fadeOut(tween(100)) },
                    label = "theme selection label",
                ) { label -> Text(label, color = colors.onSurface) }
                Icon(Icons.Default.ExpandMore, contentDescription = null, tint = colors.onSurfaceVariant)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(),
        ) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = { expanded = false; onSelect(mode) },
                    modifier = Modifier.semantics { contentDescription = "Theme option: ${mode.label}" },
                    trailingIcon = if (mode == selected) ({ Icon(Icons.Default.Check, contentDescription = "Selected", tint = colors.primary) }) else null,
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.onBackground) }
        Spacer(Modifier.width(4.dp))
        Column {
            Text(
                "FrostLock Remote",
                fontSize = 13.sp,
                letterSpacing = 0.2.sp,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
            )
            Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
                .animateContentSize(animationSpec = tween(220)),
            content = content,
        )
    }
}

@Composable
private fun FreezeDurationPicker(
    durationSeconds: Int,
    untilMinuteOfDay: Int?,
    onChange: (Int, Int?) -> Unit,
) {
    val choices = listOf(300, 900, 1800, 3600, 0, -1, -2)
    var customMinutes by remember(durationSeconds) { mutableStateOf(((durationSeconds + 59) / 60).coerceIn(5, 60).toString()) }
    var timeText by remember(untilMinuteOfDay) {
        mutableStateOf(untilMinuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "18:00")
    }
    val selected = when {
        untilMinuteOfDay != null -> -1
        durationSeconds == 0 -> 0
        durationSeconds in listOf(300, 900, 1800, 3600) -> durationSeconds
        else -> -2
    }
    val colors = MaterialTheme.colorScheme
    Text("Freeze duration", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.onSurface)
    Text("This choice is used by the one-tap PC control.", color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
    Spacer(Modifier.height(12.dp))
    choices.chunked(3).forEach { rowChoices ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rowChoices.forEach { choice ->
                val label = when (choice) {
                    300 -> "5 min"
                    900 -> "15 min"
                    1800 -> "30 min"
                    3600 -> "1 hour"
                    0 -> "Until unlock"
                    -1 -> "Until time"
                    else -> "Custom"
                }
                FilterChip(
                    selected = selected == choice,
                    onClick = {
                        when (choice) {
                            -1 -> onChange(durationSeconds, parseTime(timeText) ?: 18 * 60)
                            -2 -> onChange(customMinutes.toIntOrNull()?.coerceIn(5, 60)?.times(60) ?: 300, null)
                            else -> onChange(choice, null)
                        }
                    },
                    label = { Text(label, maxLines = 1) },
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(3 - rowChoices.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (selected == -1) {
        OutlinedTextField(
            value = timeText,
            onValueChange = { value ->
                timeText = value.take(5)
                parseTime(timeText)?.let { onChange(durationSeconds, it) }
            },
            label = { Text("Unlock at (24-hour time)") },
            supportingText = { Text("If that time has passed, it means tomorrow.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    } else if (selected == -2) {
        OutlinedTextField(
            value = customMinutes,
            onValueChange = { value ->
                customMinutes = value.filter(Char::isDigit).take(2)
                customMinutes.toIntOrNull()?.let { onChange(it.coerceIn(5, 60) * 60, null) }
            },
            label = { Text("Custom minutes (5–60)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

private fun parseTime(value: String): Int? {
    val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(value) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.onSurface)
            Text(description, color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PairedPcRow(profile: PcProfile, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(profile.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.onSurface)
            Text(profile.endpoint.removePrefix("https://"), color = colors.onSurfaceVariant, fontSize = 12.sp)
            profile.lastSeenAt?.let {
                Text("Last seen ${relativeTime(it)}", color = colors.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, "Remove ${profile.name}", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

internal fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - timestamp).coerceAtLeast(0)) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> "${minutes / (24 * 60)} d ago"
    }
}
