package com.frostlock.remote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frostlock.remote.data.PcLockSettings

@Composable
fun PcLockSettingsScreen(
    pcName: String,
    settings: PcLockSettings,
    saving: Boolean,
    error: String?,
    onSave: (PcLockSettings) -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var draft by remember(settings) { mutableStateOf(settings) }
    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            Surface(color = colors.background, shadowElevation = 6.dp) {
                Button(
                    onClick = { onSave(draft) },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(50.dp),
                ) { Text(if (saving) "Saving…" else "Save lock settings") }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Column {
                        Text("LOCK CONTROLS", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text(pcName, color = colors.onBackground, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("These settings are saved on the PC and apply to the next freeze.", color = colors.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            }
            error?.let { message -> item { Text(message, color = colors.error, modifier = Modifier.padding(horizontal = 4.dp)) } }
            item {
                Section("Lock mode") {
                    Selection("Default mode", draft.mode, listOf("Gentle", "Strict") + if (settings.hasPin) listOf("Pin") else emptyList()) {
                        draft = draft.copy(mode = it)
                    }
                    Text("Hard and USB Key modes remain PC-only. PIN mode is available only when a PIN is configured on the PC.", color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                    if (draft.mode.equals("Pin", true)) {
                        Text(
                            if (draft.mobilePinReleaseAllowed) "PIN mode selected. Phone release is enabled by the PC owner." else "PIN mode selected. Phone release is OFF; unlock with the PIN or change the PC's explicit mobile-release policy.",
                            color = if (draft.mobilePinReleaseAllowed) colors.tertiary else colors.error,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                    if (draft.mode.equals("Strict", true)) {
                        SettingToggle("Block all keys", "Strict mode blocks regular typing as well as system shortcuts.", draft.blockAllKeys) { draft = draft.copy(blockAllKeys = it) }
                        SettingToggle("Block mouse input", "Prevent mouse clicks and movement while frozen.", draft.blockMouse) { draft = draft.copy(blockMouse = it) }
                    }
                    SettingToggle("Block system shortcuts", "Swallow Windows, Alt+Tab, Alt+F4 and similar escape shortcuts.", draft.blockSystemKeys) { draft = draft.copy(blockSystemKeys = it) }
                }
            }
            item {
                Section("Duration & safety") {
                    Selection("Default duration", durationLabel(draft.durationSeconds), DURATION_CHOICES.map { durationLabel(it) }) { label ->
                        draft = draft.copy(durationSeconds = DURATION_CHOICES.first { durationLabel(it) == label })
                    }
                    Text("Until I unlock disables the timer. Any PC safety auto-release limit still applies.", color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                    Text("Arm delay: ${draft.armSeconds} sec", color = colors.onSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                    Slider(value = draft.armSeconds.toFloat(), onValueChange = { draft = draft.copy(armSeconds = it.toInt()) }, valueRange = 0f..120f, steps = 119)
                    Selection("Safety auto-release", safetyLabel(draft.safetyMaxSeconds), SAFETY_CHOICES.map(::safetyLabel)) { label ->
                        draft = draft.copy(safetyMaxSeconds = SAFETY_CHOICES.first { safetyLabel(it) == label })
                    }
                    SettingToggle("Lock Windows when freeze ends", "Lock the Windows session after a freeze is released or expires.", draft.lockWindowsOnRelease) { draft = draft.copy(lockWindowsOnRelease = it) }
                }
            }
            item {
                Section("Lock screen") {
                    Selection("Screen style", draft.lockScreenStyle, listOf("Solid", "Dim", "None")) { draft = draft.copy(lockScreenStyle = it) }
                    if (draft.lockScreenStyle.equals("None", true)) {
                        Text("No lock screen is drawn. For an indefinite freeze, make sure you have a reliable unlock hotkey or another recovery path configured on the PC.", color = colors.error, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    Selection("Displays", if (draft.lockScreenOnAllDisplays) "All displays" else "Primary only", listOf("All displays", "Primary only")) {
                        draft = draft.copy(lockScreenOnAllDisplays = it == "All displays")
                    }
                    SettingToggle("Show countdown", "Display remaining time on the PC lock screen.", draft.showCountdownOnOverlay) { draft = draft.copy(showCountdownOnOverlay = it) }
                    SettingToggle("Show clock", "Display a wall clock and the projected unlock time.", draft.showClockOnOverlay) { draft = draft.copy(showClockOnOverlay = it) }
                    OutlinedTextField(
                        value = draft.lockScreenMessage,
                        onValueChange = { draft = draft.copy(lockScreenMessage = it.take(120)) },
                        label = { Text("Lock screen message") },
                        supportingText = { Text("Optional (${draft.lockScreenMessage.length}/120)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Background images are managed locally on the PC; the phone does not send file paths.", color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            item {
                Section("Behavior") {
                    SettingToggle("Keep display awake", "Prevent the PC display from sleeping during a freeze.", draft.keepDisplayAwake) { draft = draft.copy(keepDisplayAwake = it) }
                    SettingToggle("Play sound", "Beep when the freeze starts and ends.", draft.playSound) { draft = draft.copy(playSound = it) }
                }
            }
            item {
                Section("PC security") {
                    Text("PIN and emergency-phrase secrets cannot be read or changed from the phone. Configure them on the PC. Remote PIN release remains controlled by the PC owner.", color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp).animateContentSize(animationSpec = tween(220)),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun Selection(title: String, selected: String, choices: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        androidx.compose.foundation.layout.Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = { (fadeIn(tween(140)) + androidx.compose.animation.slideInVertically(tween(160)) { it / 5 }) togetherWith fadeOut(tween(100)) },
                    label = "setting choice label",
                ) { label -> Text(label) }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(text = { Text(choice) }, onClick = { expanded = false; onSelect(choice) })
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private val DURATION_CHOICES = listOf(0, 300, 900, 1800, 3600, 7200, 14400, 28800, 86400)
private val SAFETY_CHOICES = listOf(0, 1800, 3600, 7200, 14400, 43200, 86400)

private fun durationLabel(seconds: Int): String = when {
    seconds <= 0 -> "Until I unlock"
    seconds % 3600 == 0 -> "${seconds / 3600} hr"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "$seconds sec"
}

private fun safetyLabel(seconds: Int): String =
    if (seconds <= 0) "Off" else "${seconds / 60} min"
