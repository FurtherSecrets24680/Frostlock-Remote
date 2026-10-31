package com.frostlock.remote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.frostlock.remote.data.*
import com.frostlock.remote.ui.FrostTheme
import com.frostlock.remote.ui.PcLockSettingsScreen
import com.frostlock.remote.ui.SettingsScreen
import com.frostlock.remote.ui.relativeTime
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private enum class Screen { HOME, PAIR, SETTINGS, LOCK_SETTINGS }
private data class PcState(val profile: PcProfile, val snapshot: FreezeSnapshot? = null, val checking: Boolean = true, val error: String? = null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ProfileStore(this)
        val signer = DeviceSigner()
        val api = FrostLockApi(signer)
        val preferences = PreferencesStore(this)
        setContent {
            val loaded by preferences.flow.collectAsState(initial = null)
            val prefs = loaded
            if (prefs == null) {
                // Avoid a light-to-dark flash while the first preference read completes.
                FrostTheme(ThemeMode.SYSTEM) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                }
            } else {
                FrostTheme(prefs.theme) { RemoteApp(store, api, signer, preferences, prefs) }
            }
        }
    }
}

@Composable
@androidx.camera.core.ExperimentalGetImage
private fun RemoteApp(
    store: ProfileStore,
    api: FrostLockApi,
    signer: DeviceSigner,
    preferences: PreferencesStore,
    initialPreferences: AppPreferences,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pcs by remember { mutableStateOf<List<PcState>>(emptyList()) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var banner by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var askUnlock by remember { mutableStateOf<PcState?>(null) }
    var askFreeze by remember { mutableStateOf<PcState?>(null) }
    var details by remember { mutableStateOf<PcState?>(null) }
    var renaming by remember { mutableStateOf<PcState?>(null) }
    var lockSettingsPc by remember { mutableStateOf<PcState?>(null) }
    var lockSettings by remember { mutableStateOf<PcLockSettings?>(null) }
    var lockSettingsLoading by remember { mutableStateOf(false) }
    var lockSettingsSaving by remember { mutableStateOf(false) }
    var lockSettingsError by remember { mutableStateOf<String?>(null) }
    var lockSettingsLoadVersion by remember { mutableIntStateOf(0) }

    var prefs by remember { mutableStateOf(initialPreferences) }
    LaunchedEffect(initialPreferences) { prefs = initialPreferences }

    fun applyPreferences(transform: (AppPreferences) -> AppPreferences) {
        val next = transform(prefs).normalized()
        prefs = next
        scope.launch { withContext(Dispatchers.IO) { preferences.save(next) } }
    }

    fun feedback() {
        if (prefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun refresh(profile: PcProfile) {
        scope.launch {
            pcs = pcs.map { if (it.profile.id == profile.id) it.copy(checking = true, error = null) else it }
            runCatching { withContext(Dispatchers.IO) { api.status(profile) } }
                .onSuccess { snapshot ->
                    val updated = profile.copy(lastSeenAt = System.currentTimeMillis())
                    withContext(Dispatchers.IO) { store.save(updated) }
                    pcs = pcs.map { if (it.profile.id == profile.id) PcState(updated, snapshot, false) else it }
                }
                .onFailure { err -> pcs = pcs.map { if (it.profile.id == profile.id) it.copy(checking = false, error = err.message ?: "PC unreachable") else it } }
        }
    }

    fun freeze(pc: PcState, minutes: Int, mode: String? = null) {
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.freeze(pc.profile, minutes * 60, mode) } }
                .onSuccess { snapshot ->
                    pcs = pcs.map { if (it.profile.id == pc.profile.id) it.copy(snapshot = snapshot, error = null) else it }
                    banner = "Freeze confirmed by ${pc.profile.name} for $minutes minutes."
                    feedback()
                }
                .onFailure { banner = it.message ?: "Freeze was not confirmed. Refresh status before retrying." }
            busy = false
        }
    }

    fun unlock(pc: PcState) {
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.unlock(pc.profile) } }
                .onSuccess { snapshot ->
                    pcs = pcs.map { if (it.profile.id == pc.profile.id) it.copy(snapshot = snapshot, error = null) else it }
                    banner = if (snapshot.isActive) "PC reports the freeze is still active." else "Unlock confirmed by ${pc.profile.name}."
                    if (!snapshot.isActive) feedback()
                }
                .onFailure { banner = it.message ?: "Unlock was not confirmed. Refresh status before retrying." }
            busy = false
        }
    }

    fun removePc(profile: PcProfile) {
        scope.launch {
            withContext(Dispatchers.IO) {
                store.remove(profile.id)
                signer.delete(profile.keyAlias)
            }
            pcs = pcs.filterNot { it.profile.id == profile.id }
            details = null
            banner = "Removed ${profile.name} from this phone. Revoke it on the PC too if the phone is lost."
        }
    }

    LaunchedEffect(Unit) {
        pcs = withContext(Dispatchers.IO) { store.all().sortedByDescending { it.lastSeenAt ?: it.pairedAt } }.map(::PcState)
        pcs.forEach { refresh(it.profile) }
    }

    LaunchedEffect(lockSettingsPc?.profile?.id, lockSettingsLoadVersion) {
        val profile = lockSettingsPc?.profile ?: return@LaunchedEffect
        lockSettingsLoading = true
        lockSettingsError = null
        runCatching { withContext(Dispatchers.IO) { api.lockSettings(profile) } }
            .onSuccess { lockSettings = it }
            .onFailure { lockSettingsError = it.message ?: "Could not load lock settings." }
        lockSettingsLoading = false
    }

    BackHandler(enabled = screen != Screen.HOME) {
        screen = Screen.HOME
        banner = null
    }

    AnimatedContent(
        targetState = screen,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        transitionSpec = {
            (if (targetState == Screen.HOME) {
                (slideInHorizontally(tween(220)) { -it / 12 } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(tween(160)) { it / 16 } + fadeOut(tween(140)))
            } else {
                (slideInHorizontally(tween(220)) { it / 12 } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(tween(160)) { -it / 16 } + fadeOut(tween(140)))
            }).using(SizeTransform(clip = false))
        },
        label = "main page transition",
    ) { targetScreen ->
    when (targetScreen) {
        Screen.PAIR -> PairScreen(
            onBack = { screen = Screen.HOME; banner = null },
            onInvite = { raw ->
                scope.launch {
                    try {
                        busy = true
                        banner = null
                        val invite = PairingInvite.parse(raw)
                        val alias = "frostlock-${java.util.UUID.randomUUID()}"
                        val profile = withContext(Dispatchers.IO) {
                            api.pair(invite, android.os.Build.MODEL.take(32), alias)
                        }
                        withContext(Dispatchers.IO) { store.save(profile) }
                        pcs = pcs + PcState(profile)
                        screen = Screen.HOME
                        banner = "Pairing approved by ${profile.name}."
                        feedback()
                        refresh(profile)
                    } catch (error: Throwable) {
                        banner = pairingError(error)
                    } finally {
                        busy = false
                    }
                }
            },
            busy = busy,
            banner = banner,
            onDismissBanner = { banner = null },
        )

        Screen.SETTINGS -> SettingsScreen(
            preferences = prefs,
            versionName = BuildConfig.VERSION_NAME,
            pcs = pcs.map { it.profile },
            onThemeChange = { mode -> applyPreferences { it.copy(theme = mode) } },
            onDefaultMinutesChange = { minutes -> applyPreferences { it.copy(defaultFreezeMinutes = minutes) } },
            onConfirmFreezeChange = { enabled -> applyPreferences { it.copy(confirmFreeze = enabled) } },
            onConfirmUnlockChange = { enabled -> applyPreferences { it.copy(confirmUnlock = enabled) } },
            onHapticsChange = { enabled -> applyPreferences { it.copy(haptics = enabled) } },
            onRemovePc = ::removePc,
            onReset = { applyPreferences { AppPreferences() } },
            onBack = { screen = Screen.HOME; banner = null },
        )

        Screen.LOCK_SETTINGS -> {
            val pc = lockSettingsPc
            when {
                pc == null -> Text("Select a PC to manage its lock settings.")
                lockSettingsLoading && lockSettings == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                lockSettings == null -> Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Lock settings unavailable", style = MaterialTheme.typography.headlineSmall)
                        Text(lockSettingsError ?: "FrostLock on this PC may need an update to support remote lock settings.")
                        TextButton(onClick = { lockSettingsLoadVersion++ }) { Text("Retry") }
                        TextButton(onClick = { screen = Screen.HOME }) { Text("Back") }
                    }
                }
                else -> PcLockSettingsScreen(
                    pcName = pc.profile.name,
                    settings = lockSettings!!,
                    saving = lockSettingsSaving,
                    error = lockSettingsError,
                    onBack = { screen = Screen.HOME },
                    onSave = { draft ->
                        lockSettingsSaving = true
                        lockSettingsError = null
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { api.updateLockSettings(pc.profile, draft) } }
                                .onSuccess {
                                    lockSettings = it
                                    banner = "Lock settings saved on ${pc.profile.name}."
                                    feedback()
                                }
                                .onFailure { lockSettingsError = it.message ?: "Could not save lock settings." }
                            lockSettingsSaving = false
                        }
                    },
                )
            }
        }

        Screen.HOME -> HomeScreen(
            pcs = pcs,
            busy = busy,
            banner = banner,
            onDismissBanner = { banner = null },
            onSettings = { screen = Screen.SETTINGS },
            onLockSettings = { pc -> lockSettings = null; lockSettingsPc = pc; screen = Screen.LOCK_SETTINGS },
            onPair = { screen = Screen.PAIR },
            onRefresh = { pcs.forEach { refresh(it.profile) } },
            onFreeze = { pc ->
                if (prefs.confirmFreeze) askFreeze = pc else freeze(pc, prefs.defaultFreezeMinutes)
            },
            onUnlock = { pc ->
                if (prefs.confirmUnlock) askUnlock = pc else unlock(pc)
            },
            onDetails = { details = it },
            onRename = { renaming = it },
            onRemove = { pc -> removePc(pc.profile) },
        )
    }
    }

    askFreeze?.let { pc ->
        var duration by remember(pc.profile.id) { mutableIntStateOf(prefs.defaultFreezeMinutes) }
        var selectedMode by remember(pc.profile.id) { mutableStateOf<String?>(null) }
        var pcLockSettings by remember(pc.profile.id) { mutableStateOf<PcLockSettings?>(null) }
        val durationOptions = (listOf(0, 5, 15, 30, 60) + prefs.defaultFreezeMinutes + (duration.takeIf { it > 60 } ?: -1))
            .filter { it >= 0 }.distinct().sorted()
            .associate { minutes -> (if (minutes == 0) "Until I unlock" else formatDurationChoice(minutes)) to minutes }
        LaunchedEffect(pc.profile.id) {
            runCatching { withContext(Dispatchers.IO) { api.lockSettings(pc.profile) } }
                .onSuccess {
                    pcLockSettings = it
                    duration = if (it.durationSeconds <= 0) 0 else (it.durationSeconds + 59) / 60
                    selectedMode = it.mode
                }
        }
        AlertDialog(
            onDismissRequest = { askFreeze = null },
            title = { Text("Freeze ${pc.profile.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose how this PC locks and for how long. Hard and USB Key are PC-only modes.", fontSize = 14.sp, lineHeight = 20.sp)
                    FreezeOptionDropdown(
                        label = "Lock mode",
                        value = selectedMode?.let { if (it.equals("Pin", true)) "PIN" else it } ?: "Choose mode",
                        options = listOf("Gentle", "Strict") + if (pcLockSettings?.hasPin == true) listOf("PIN") else emptyList(),
                        onSelected = { selectedMode = if (it == "PIN") "Pin" else it },
                    )
                    Text(
                        when {
                            selectedMode.equals("Pin", true) -> "PIN-protected lock. Release follows the PC's PIN policy."
                            selectedMode.equals("Strict", true) -> "Blocks keyboard and mouse input according to the PC's Strict settings."
                            else -> "Shows the lock screen while leaving ordinary mouse input available."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    AnimatedVisibility(visible = selectedMode.equals("Pin", true) && pcLockSettings?.mobilePinReleaseAllowed != true) {
                        Text("Phone release is disabled. You will need to enter the PIN on the PC.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    FreezeOptionDropdown(
                        label = "Duration",
                        value = if (duration == 0) "Until I unlock" else formatDurationChoice(duration),
                        options = durationOptions.keys.toList(),
                        onSelected = { value -> duration = durationOptions[value] ?: duration },
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    askFreeze = null
                    freeze(pc, duration, selectedMode)
                }) { Text("Freeze") }
            },
            dismissButton = { TextButton(onClick = { askFreeze = null }) { Text("Cancel") } },
        )
    }

    askUnlock?.let { pc ->
        AlertDialog(
            onDismissRequest = { askUnlock = null },
            title = { Text("Unlock ${pc.profile.name}?") },
            text = { Text(if (pc.snapshot?.mode.equals("PIN", true)) "This uses the paired phone as an alternate release credential. Continue only if you trust this device." else "This will ask FrostLock on the PC to release the active freeze. The PC decides whether the current mode allows it.") },
            confirmButton = {
                TextButton(onClick = {
                    askUnlock = null
                    unlock(pc)
                }) { Text("Unlock", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { askUnlock = null }) { Text("Cancel") } },
        )
    }

    details?.let { pc ->
        var testing by remember(pc.profile.id) { mutableStateOf(false) }
        var testResult by remember(pc.profile.id) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text(pc.profile.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailLine("Connection", pc.profile.endpoint.removePrefix("https://"))
                    DetailLine("Certificate", pc.profile.certificateSha256.chunked(8).joinToString(" "))
                    DetailLine("Paired", shortDate(pc.profile.pairedAt))
                    DetailLine("Last seen", pc.profile.lastSeenAt?.let { relativeTime(it) } ?: "Never")
                    AnimatedVisibility(visible = testResult != null) {
                        Text(
                            testResult.orEmpty(),
                            color = if (testResult?.startsWith("Reachable") == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !testing,
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            val started = System.nanoTime()
                            runCatching { withContext(Dispatchers.IO) { api.status(pc.profile) } }
                                .onSuccess { testResult = "Reachable in ${(System.nanoTime() - started) / 1_000_000} ms" }
                                .onFailure { testResult = it.message ?: "The PC did not answer." }
                            testing = false
                        }
                    },
                ) { Text(if (testing) "Testing…" else "Test connection") }
            },
            dismissButton = { TextButton(onClick = { details = null }) { Text("Close") } },
        )
    }

    renaming?.let { pc ->
        var value by remember(pc.profile.id) { mutableStateOf(pc.profile.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename this PC") },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(40) },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = value.isNotBlank(),
                    onClick = {
                        val renamed = pc.profile.copy(name = value.trim())
                        renaming = null
                        scope.launch {
                            withContext(Dispatchers.IO) { store.save(renamed) }
                            pcs = pcs.map { if (it.profile.id == renamed.id) it.copy(profile = renamed) else it }
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(
            label.uppercase(),
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = if (label == "Certificate") FontFamily.Monospace else FontFamily.Default,
        )
    }
}

@Composable
private fun FreezeOptionDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    AnimatedContent(
                        targetState = value,
                        transitionSpec = { (fadeIn(tween(140)) + androidx.compose.animation.slideInVertically(tween(160)) { it / 5 }) togetherWith fadeOut(tween(100)) },
                        label = "$label selection label",
                    ) { label -> Text(label, color = MaterialTheme.colorScheme.onSurface) }
                    Icon(Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 200.dp, max = 300.dp),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { expanded = false; onSelected(option) },
                    )
                }
            }
        }
    }
}

private fun formatDurationChoice(minutes: Int): String = when {
    minutes % 60 == 0 && minutes >= 60 -> "${minutes / 60} hr (${minutes} min)"
    else -> "$minutes min"
}

private fun shortDate(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun HomeScreen(
    pcs: List<PcState>, busy: Boolean, banner: String?, onDismissBanner: () -> Unit,
    onSettings: () -> Unit, onLockSettings: (PcState) -> Unit, onPair: () -> Unit, onRefresh: () -> Unit,
    onFreeze: (PcState) -> Unit, onUnlock: (PcState) -> Unit,
    onDetails: (PcState) -> Unit, onRename: (PcState) -> Unit, onRemove: (PcState) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(containerColor = colors.background) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(com.frostlock.remote.R.mipmap.ic_launcher_foreground),
                        contentDescription = "FrostLock",
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((-3).dp)) {
                        Text("FrostLock", fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                        Text("Remote", fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
                    }
                    FilledIconButton(
                        onClick = onPair,
                        modifier = Modifier.size(42.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                    ) { Icon(Icons.Default.Add, contentDescription = "Pair a PC") }
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh status", tint = colors.onBackground) }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = colors.onBackground) }
                }
                Spacer(Modifier.height(13.dp))
                Text("Your PCs", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Text("Private network control. Your PC stays in charge.", color = colors.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(top = 5.dp))
            }
            item {
                AnimatedVisibility(
                    visible = banner != null,
                    enter = fadeIn(tween(180)) + androidx.compose.animation.slideInVertically(tween(220)) { -it / 3 },
                    exit = fadeOut(tween(120)) + androidx.compose.animation.slideOutVertically(tween(160)) { -it / 4 },
                ) {
                    Surface(color = colors.secondaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onDismissBanner)) {
                        Text(banner.orEmpty(), Modifier.padding(14.dp), color = colors.onSecondaryContainer, fontSize = 13.sp)
                    }
                }
            }
            item {
                AnimatedVisibility(
                    visible = pcs.isEmpty(),
                    enter = fadeIn(tween(240)) + androidx.compose.animation.slideInVertically(tween(260)) { it / 10 },
                    exit = fadeOut(tween(120)) + androidx.compose.animation.slideOutVertically(tween(160)) { it / 12 },
                ) { EmptyState(onPair) }
            }
            items(pcs, key = { it.profile.id }) { pc ->
                PcCard(pc, busy, { onFreeze(pc) }, { onUnlock(pc) }, { onDetails(pc) }, { onLockSettings(pc) }, { onRename(pc) }, { onRemove(pc) })
            }
        }
    }
}

@Composable
private fun EmptyState(onPair: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 28.dp), horizontalAlignment = Alignment.Start) {
            Spacer(Modifier.height(2.dp))
            Text("DIRECT LOCAL CONNECTION", fontSize = 10.sp, letterSpacing = 1.25.sp, color = colors.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text("Nothing paired yet", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Spacer(Modifier.height(8.dp))
            Text("Connect this phone directly to FrostLock on your PC. Pairing is approved on the computer and stays on your local network.", color = colors.onSurfaceVariant, fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onPair,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Pair a PC") }
        }
    }
}

@Composable
private fun PcCard(
    pc: PcState, busy: Boolean,
    onFreeze: () -> Unit, onUnlock: () -> Unit,
    onDetails: () -> Unit, onLockSettings: () -> Unit, onRename: () -> Unit, onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val active = pc.snapshot?.isActive == true
    var remaining by remember(pc.snapshot) { mutableLongStateOf(pc.snapshot?.remainingSeconds ?: 0L) }
    LaunchedEffect(pc.snapshot) {
        remaining = pc.snapshot?.remainingSeconds ?: 0L
        while (pc.snapshot?.isActive == true && remaining > 0) {
            delay(1000)
            remaining = (remaining - 1).coerceAtLeast(0)
        }
    }

    Surface(color = colors.surface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp).animateContentSize(animationSpec = tween(220))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                        .background(if (pc.error == null && !pc.checking) colors.secondaryContainer else colors.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (pc.error != null) Icons.Default.WifiOff else Icons.Default.Devices,
                        null,
                        tint = if (pc.error != null) colors.onSurfaceVariant else colors.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(pc.profile.name, fontWeight = FontWeight.Bold, color = colors.onSurface, fontSize = 17.sp)
                    Text(
                        when {
                            pc.checking -> "Checking connection…"
                            pc.error != null -> "Offline. State unknown"
                            else -> "Online, ${pc.snapshot?.phase?.replaceFirstChar(Char::uppercase) ?: "Unknown"}"
                        },
                        color = if (pc.error == null && !pc.checking) colors.primary else colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, "More options for ${pc.profile.name}", tint = colors.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Connection details") }, onClick = { menu = false; onDetails() })
                        DropdownMenuItem(text = { Text("Lock settings") }, onClick = { menu = false; onLockSettings() })
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Remove from this phone") }, onClick = { menu = false; onRemove() })
                    }
                }
            }
            if (pc.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(pc.error, color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
                val lastSeen = pc.profile.lastSeenAt
                Text(
                    if (lastSeen != null) "Last seen ${relativeTime(lastSeen)}. Offline does not mean unlocked." else "Never connected from this phone. Offline does not mean unlocked.",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else if (pc.snapshot != null) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (active) Icons.Default.Lock else Icons.Default.CheckCircle,
                        null,
                        tint = if (active) colors.primary else colors.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        if (active) "${pc.snapshot.mode ?: "Freeze"} (${if (remaining > 0) formatTime(remaining) else "No timer"})" else "Idle",
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (active) {
                    OutlinedButton(
                        onClick = onUnlock,
                        enabled = pc.snapshot.remoteReleaseAllowed && !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text(if (pc.snapshot.remoteReleaseAllowed) "Review unlock" else "Unlock disabled by PC policy")
                    }
                } else {
                    Button(
                        onClick = onFreeze,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                    ) { Text("Freeze this PC") }
                }
                if (active && !pc.snapshot.remoteReleaseAllowed) {
                    Text("This mode does not allow remote release.", color = colors.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            } else if (pc.checking) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp), color = colors.primary)
            }
        }
    }
}

private fun formatTime(seconds: Long): String = "%d:%02d remaining".format(seconds / 60, seconds % 60)

private fun pairingError(error: Throwable): String = when (error) {
    is java.net.SocketTimeoutException ->
        "The PC did not answer. Check that FrostLock is running with Mobile Unlock enabled, then create a new pairing code and try again."
    is java.net.ConnectException, is java.net.NoRouteToHostException, is java.net.UnknownHostException ->
        "Could not reach the PC. After Wi-Fi reconnects, the PC address may have changed. Refresh the LAN code on Windows and choose the address on the same network as your phone."
    is javax.net.ssl.SSLException ->
        "The secure connection failed. Create a new pairing code on the PC and try again."
    else -> error.message?.takeIf(String::isNotBlank) ?: "Pairing failed. Check that the PC is running FrostLock and both devices are on the same network."
}

@Composable
@androidx.camera.core.ExperimentalGetImage
private fun PairScreen(
    onBack: () -> Unit, onInvite: (String) -> Unit, busy: Boolean,
    banner: String?, onDismissBanner: () -> Unit,
) {
    var invite by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.onBackground)
                }
            }
            Text("PAIR A PC", color = colors.primary, fontSize = 11.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp))
            Text("Connect to FrostLock", color = colors.onBackground, fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
            Text("Scan the encrypted code from your Windows PC, or paste it below.", color = colors.onSurfaceVariant, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            Surface(
                color = colors.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, colors.outlineVariant),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Shield, null, tint = colors.primary, modifier = Modifier.size(21.dp))
                    Column {
                        Text("Private, short-lived pairing", color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("The code expires after 3 minutes. Approve the phone on Windows to finish pairing.", color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (banner != null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).clickable(onClick = onDismissBanner)) {
                    Text(banner, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            if (!manual) {
                if (!hasCamera) Button(onClick = { permission.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) { Text("Allow camera to scan") }
                else QrCamera(lifecycleOwner, onCode = { invite = it; manual = true })
                TextButton(onClick = { manual = true }) { Text("Enter code manually") }
            } else {
                OutlinedTextField(
                    value = invite,
                    onValueChange = { invite = it.filterNot(Char::isWhitespace) },
                    label = { Text("Encrypted pairing code") },
                    placeholder = { Text("Paste code beginning with FL1.") },
                    minLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("The code contains no readable PC details or commands. It is decrypted on this phone and used once to request approval.", color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
        if (manual) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) {
                Button(
                    onClick = { onInvite(invite) },
                    enabled = invite.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("Pair over LAN")
                }
                Spacer(Modifier.height(8.dp))
                if (busy) Text(
                    "Waiting for approval on the PC…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
@androidx.camera.core.ExperimentalGetImage
private fun QrCamera(lifecycleOwner: androidx.lifecycle.LifecycleOwner, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val cameraProvider = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val resultSent = remember { AtomicBoolean(false) }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(22.dp)),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                cameraProvider.set(provider)
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { imageProxy ->
                    val media = imageProxy.image
                    if (media == null) imageProxy.close() else {
                        scanner.process(InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees))
                            .addOnSuccessListener { codes ->
                                val value = codes.firstOrNull()?.rawValue
                                if (value != null && resultSent.compareAndSet(false, true)) onCode(value)
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    }
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
    DisposableEffect(Unit) {
        onDispose { cameraProvider.getAndSet(null)?.unbindAll(); scanner.close(); executor.shutdown() }
    }
}
