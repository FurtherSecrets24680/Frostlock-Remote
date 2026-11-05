package com.frostlock.remote.ui

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import com.frostlock.remote.data.FrostLockApi
import com.frostlock.remote.data.PcProfile
import com.frostlock.remote.data.WindowsTask
import com.frostlock.remote.data.WindowsTaskSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private enum class TaskSort(val label: String) {
    CPU("CPU"),
    MEMORY("Memory"),
    DISK("Disk"),
    NETWORK("Network"),
    NAME("Name"),
}

private enum class TaskFilter(val label: String) {
    ALL("All"),
    APPS("Apps"),
    BACKGROUND("Background"),
}

private object TaskPalette {
    val backgroundTop = Color(0xFF081B3A)
    val backgroundMid = Color(0xFF06142B)
    val backgroundBottom = Color(0xFF040B18)
    val card = Color(0xFF0D2447)
    val cardDeep = Color(0xFF091A35)
    val border = Color(0x335E9BE0)
    val borderSoft = Color(0x225E9BE0)
    val text = Color(0xFFF2F7FF)
    val muted = Color(0xFF93A8C9)
    val faint = Color(0xFF6C82A6)
    val blue = Color(0xFF4DA3FF)
    val blueDeep = Color(0xFF2E7CF5)
    val purple = Color(0xFFB28CFF)
    val green = Color(0xFF4CD7A0)
    val orange = Color(0xFFFFB454)
    val red = Color(0xFFFF6B81)
    val chip = Color(0x1A7DA9E8)
}

private val TaskDarkScheme = darkColorScheme(
    primary = TaskPalette.blue,
    onPrimary = Color(0xFF04101F),
    secondary = TaskPalette.purple,
    background = TaskPalette.backgroundMid,
    onBackground = TaskPalette.text,
    surface = TaskPalette.card,
    onSurface = TaskPalette.text,
    surfaceVariant = Color(0xFF122A4F),
    onSurfaceVariant = TaskPalette.muted,
    outline = TaskPalette.border,
    outlineVariant = TaskPalette.borderSoft,
    error = TaskPalette.red,
    onError = Color(0xFF20060C),
    errorContainer = Color(0xFF3A1420),
    onErrorContainer = Color(0xFFFFC2CB),
)

private val TaskGlassBrush = Brush.verticalGradient(
    0f to Color(0xE60E274E),
    1f to Color(0xE6081A35),
)

private val TaskBackgroundBrush = Brush.verticalGradient(
    0f to TaskPalette.backgroundTop,
    0.45f to TaskPalette.backgroundMid,
    1f to TaskPalette.backgroundBottom,
)

private const val HistoryLimit = 32

private data class MetricHistory(
    val cpu: List<Float> = emptyList(),
    val memory: List<Float> = emptyList(),
    val disk: List<Float> = emptyList(),
    val network: List<Float> = emptyList(),
) {
    fun append(task: WindowsTask): MetricHistory = MetricHistory(
        cpu = (cpu + task.cpuPercent.toFloat()).takeLast(HistoryLimit),
        memory = (memory + task.workingSetBytes / 1048576f).takeLast(HistoryLimit),
        disk = (disk + (task.diskReadBytesPerSec + task.diskWriteBytesPerSec).toFloat()).takeLast(HistoryLimit),
        network = (network + task.connectionCount.toFloat()).takeLast(HistoryLimit),
    )
}

private data class SystemHistory(
    val cpu: List<Float> = emptyList(),
    val memory: List<Float> = emptyList(),
    val processes: List<Float> = emptyList(),
    val network: List<Float> = emptyList(),
) {
    fun append(snapshot: WindowsTaskSnapshot): SystemHistory = SystemHistory(
        cpu = (cpu + snapshot.totalCpuPercent.toFloat()).takeLast(HistoryLimit),
        memory = (memory + snapshot.usedMemoryBytes / 1048576f).takeLast(HistoryLimit),
        processes = (processes + snapshot.processCount.toFloat()).takeLast(HistoryLimit),
        network = (
            network + ((snapshot.networkReceiveBytesPerSec + snapshot.networkSendBytesPerSec) / 1024f)
            ).takeLast(HistoryLimit),
    )
}

@Composable
fun TaskManagerScreen(
    api: FrostLockApi,
    profile: PcProfile,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<WindowsTaskSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var autoRefresh by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(TaskSort.CPU) }
    var filter by remember { mutableStateOf(TaskFilter.ALL) }
    var selected by remember { mutableStateOf<WindowsTask?>(null) }
    var pendingKill by remember { mutableStateOf<WindowsTask?>(null) }
    var startOpen by remember { mutableStateOf(false) }
    var actionBusy by remember { mutableStateOf(false) }
    var systemHistory by remember { mutableStateOf(SystemHistory()) }
    val processHistory = remember { mutableStateMapOf<Int, MetricHistory>() }
    val icons = remember { mutableStateMapOf<String, ImageBitmap>() }

    TaskDarkSystemBars()

    suspend fun loadSnapshot(showSpinner: Boolean) {
        if (showSpinner) refreshing = true
        try {
            val fresh = withContext(Dispatchers.IO) { api.taskList(profile) }
            snapshot = fresh
            error = null
        } catch (problem: Throwable) {
            error = problem.message ?: "Could not load the task list from this PC."
        } finally {
            if (showSpinner) refreshing = false
        }
    }

    fun endTask(task: WindowsTask, force: Boolean) {
        if (actionBusy) return
        actionBusy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.endTask(profile, task.pid, force) } }
                .onSuccess {
                    pendingKill = null
                    selected = null
                    onToast("Ended ${task.displayName} on ${profile.name}.")
                    loadSnapshot(false)
                }
                .onFailure { onToast(it.message ?: "FrostLock could not end that task.") }
            actionBusy = false
        }
    }

    LaunchedEffect(profile.id) { loadSnapshot(true) }
    LaunchedEffect(profile.id, autoRefresh) {
        if (!autoRefresh) return@LaunchedEffect
        while (true) {
            delay(4000)
            loadSnapshot(false)
        }
    }

    LaunchedEffect(snapshot?.sampledAtMs) {
        val current = snapshot ?: return@LaunchedEffect
        systemHistory = systemHistory.append(current)
        current.processes.forEach { task ->
            processHistory[task.pid] = (processHistory[task.pid] ?: MetricHistory()).append(task)
        }
        val alive = current.processes.mapTo(HashSet()) { it.pid }
        processHistory.keys.retainAll(alive)
    }

    LaunchedEffect(snapshot?.sampledAtMs) {
        val keys = snapshot?.processes
            ?.mapNotNull(WindowsTask::iconKey)
            ?.filter { it !in icons }
            ?.distinct()
            .orEmpty()
        if (keys.isEmpty()) return@LaunchedEffect
        val loaded = runCatching {
            withContext(Dispatchers.IO) {
                api.taskIcons(profile, keys).mapNotNull { (key, bytes) ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { key to it.asImageBitmap() }
                }.toMap()
            }
        }.getOrDefault(emptyMap())
        loaded.forEach { (key, bitmap) -> icons[key] = bitmap }
    }

    val processes = remember(snapshot, search, sort, filter) {
        val query = search.trim()
        snapshot?.processes.orEmpty()
            .filter { task ->
                when (filter) {
                    TaskFilter.ALL -> true
                    TaskFilter.APPS -> task.hasWindow
                    TaskFilter.BACKGROUND -> !task.hasWindow
                }
            }
            .filter { task ->
                query.isEmpty() || listOfNotNull(task.displayName, task.name, task.publisher, task.path)
                    .any { it.contains(query, ignoreCase = true) }
            }
            .sortedWith(
                when (sort) {
                    TaskSort.CPU -> compareByDescending<WindowsTask> { it.cpuPercent }
                        .thenByDescending { it.workingSetBytes }
                    TaskSort.MEMORY -> compareByDescending<WindowsTask> { it.workingSetBytes }
                    TaskSort.DISK -> compareByDescending { it.diskReadBytesPerSec + it.diskWriteBytesPerSec }
                    TaskSort.NETWORK -> compareByDescending { it.connectionCount }
                    TaskSort.NAME -> compareBy { it.displayName.lowercase(Locale.getDefault()) }
                },
            )
    }

    MaterialTheme(colorScheme = TaskDarkScheme) {
        Box(Modifier.fillMaxSize().background(TaskBackgroundBrush)) {
            TaskBackgroundGlow()
            Scaffold(containerColor = Color.Transparent) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    TaskTopBar(
                        machine = snapshot?.machineName ?: profile.name,
                        live = autoRefresh,
                        refreshing = refreshing,
                        onBack = onBack,
                        onToggleLive = { autoRefresh = !autoRefresh },
                        onRefresh = { scope.launch { loadSnapshot(true) } },
                    )

                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "summary") {
                            MachineCard(
                                snapshot = snapshot,
                                history = systemHistory,
                                connected = snapshot != null && error == null,
                                connecting = snapshot == null && error == null,
                            )
                        }

                        item(key = "search") {
                            TaskSearchField(
                                value = search,
                                onValueChange = { search = it.take(80) },
                                onClear = { search = "" },
                            )
                        }

                        item(key = "filters") {
                            TaskFilterRow(
                                filter = filter,
                                sort = sort,
                                onFilter = { filter = it },
                                onSort = { sort = it },
                                onStart = { startOpen = true },
                            )
                        }

                        if (error != null) {
                            item(key = "error") {
                                TaskErrorCard(error.orEmpty()) {
                                    scope.launch { loadSnapshot(true) }
                                }
                            }
                        }

                        item(key = "count") {
                            TaskCountRow(
                                count = processes.size,
                                snapshot = snapshot,
                                autoRefresh = autoRefresh,
                            )
                        }

                        if (snapshot == null && refreshing) {
                            item(key = "loading") {
                                Box(
                                    Modifier.fillMaxWidth().padding(top = 56.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = TaskPalette.blue)
                                }
                            }
                        }

                        items(processes, key = { it.pid }) { task ->
                            TaskRow(
                                task = task,
                                icon = task.iconKey?.let(icons::get),
                                onClick = { selected = task },
                            )
                        }
                    }
                }
            }
        }

        selected?.let { task ->
            val live = snapshot?.processes?.firstOrNull { it.pid == task.pid } ?: task
            val running = snapshot?.processes?.any { it.pid == task.pid } ?: true
            TaskDetailDialog(
                task = live,
                icon = live.iconKey?.let(icons::get),
                history = processHistory[live.pid] ?: MetricHistory(),
                running = running,
                onDismiss = { selected = null },
                onEnd = {
                    selected = null
                    pendingKill = live
                },
                onCopy = { text ->
                    onToast(if (text) "Location copied." else "Nothing to copy.")
                },
            )
        }

        pendingKill?.let { task ->
            EndTaskDialog(
                task = task,
                busy = actionBusy,
                onDismiss = { if (!actionBusy) pendingKill = null },
                onEnd = { force -> endTask(task, force) },
            )
        }

        if (startOpen) {
            StartTaskDialog(
                profile = profile,
                busy = actionBusy,
                onDismiss = { if (!actionBusy) startOpen = false },
                onStart = { path, arguments, directory ->
                    actionBusy = true
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) { api.startTask(profile, path, arguments, directory) }
                        }.onSuccess { started ->
                            startOpen = false
                            onToast("Started ${started.name} (PID ${started.pid}) on ${profile.name}.")
                            loadSnapshot(false)
                        }.onFailure {
                            onToast(it.message ?: "FrostLock could not start that program on the PC.")
                        }
                        actionBusy = false
                    }
                },
            )
        }
    }
}

@Composable
private fun TaskDarkSystemBars() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
            ?: return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        val previousLightStatus = controller.isAppearanceLightStatusBars
        val previousLightNavigation = controller.isAppearanceLightNavigationBars
        @Suppress("DEPRECATION") val previousStatus = window.statusBarColor
        @Suppress("DEPRECATION") val previousNavigation = window.navigationBarColor
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        @Suppress("DEPRECATION") window.statusBarColor = 0xFF06142B.toInt()
        @Suppress("DEPRECATION") window.navigationBarColor = 0xFF040B18.toInt()
        onDispose {
            controller.isAppearanceLightStatusBars = previousLightStatus
            controller.isAppearanceLightNavigationBars = previousLightNavigation
            @Suppress("DEPRECATION") window.statusBarColor = previousStatus
            @Suppress("DEPRECATION") window.navigationBarColor = previousNavigation
        }
    }
}

@Composable
private fun TaskBackgroundGlow() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(TaskPalette.blue.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(size.width * 0.88f, size.height * 0.04f),
                radius = size.width * 0.85f,
            ),
            radius = size.width * 0.85f,
            center = Offset(size.width * 0.88f, size.height * 0.04f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(TaskPalette.purple.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height * 0.72f),
                radius = size.width * 0.7f,
            ),
            radius = size.width * 0.7f,
            center = Offset(size.width * 0.05f, size.height * 0.72f),
        )
    }
}

@Composable
private fun TaskTopBar(
    machine: String,
    live: Boolean,
    refreshing: Boolean,
    onBack: () -> Unit,
    onToggleLive: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleGlassButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                "Back to $machine",
                tint = TaskPalette.text,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Task manager",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TaskPalette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                machine,
                fontSize = 12.sp,
                color = TaskPalette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        GlassPill(
            onClick = onToggleLive,
            borderColor = TaskPalette.blue.copy(alpha = 0.35f),
        ) {
            Icon(
                if (live) Icons.Default.Pause else Icons.Default.PlayArrow,
                null,
                tint = TaskPalette.blue,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (live) "Pause" else "Live",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TaskPalette.text,
            )
        }
        Spacer(Modifier.width(8.dp))
        CircleGlassButton(onClick = onRefresh, enabled = !refreshing) {
            if (refreshing) {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = TaskPalette.blue,
                )
            } else {
                Icon(
                    Icons.Default.Refresh,
                    "Refresh tasks",
                    tint = TaskPalette.text,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

@Composable
private fun MachineCard(
    snapshot: WindowsTaskSnapshot?,
    history: SystemHistory,
    connected: Boolean,
    connecting: Boolean,
) {
    GlassSurface(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF3E8BFF), Color(0xFF6FC2FF)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.GridView,
                        null,
                        tint = Color(0xFF06142B),
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        snapshot?.machineName ?: "Windows PC",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = TaskPalette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(7.dp).clip(CircleShape).background(
                                when {
                                    connected -> TaskPalette.green
                                    connecting -> TaskPalette.orange
                                    else -> TaskPalette.red
                                },
                            ),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                connected -> "Connected"
                                connecting -> "Connecting"
                                else -> "Connection issue"
                            },
                            fontSize = 12.sp,
                            color = TaskPalette.muted,
                        )
                    }
                }
                if (snapshot?.controllerElevated == true) {
                    GlassPill(borderColor = TaskPalette.tertiaryBorder(), onClick = {}) {
                        Icon(
                            Icons.Default.Shield,
                            null,
                            tint = TaskPalette.purple,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Admin", fontSize = 10.sp, color = TaskPalette.purple)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SummaryTile(
                    icon = Icons.Default.Speed,
                    tint = TaskPalette.blue,
                    label = "CPU",
                    value = snapshot?.let { String.format(Locale.US, "%.1f%%", it.totalCpuPercent) } ?: "—",
                    caption = "System total",
                    values = history.cpu,
                    modifier = Modifier.weight(1f),
                )
                SummaryTile(
                    icon = Icons.Default.Memory,
                    tint = TaskPalette.purple,
                    label = "Memory",
                    value = snapshot?.let { formatBytes(it.usedMemoryBytes) } ?: "—",
                    caption = snapshot?.let { "of ${formatBytes(it.totalMemoryBytes)}" } ?: "physical RAM",
                    values = history.memory,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SummaryTile(
                    icon = Icons.Default.Layers,
                    tint = TaskPalette.green,
                    label = "Processes",
                    value = snapshot?.processCount?.toString() ?: "—",
                    caption = snapshot?.let { "${it.appCount} apps · ${it.connectionCount} sockets" } ?: "Windows tasks",
                    values = history.processes,
                    modifier = Modifier.weight(1f),
                )
                SummaryTile(
                    icon = Icons.Default.Language,
                    tint = TaskPalette.orange,
                    label = "Network",
                    value = snapshot?.let {
                        "${formatBytes(it.networkReceiveBytesPerSec + it.networkSendBytesPerSec)}/s"
                    } ?: "—",
                    caption = snapshot?.let {
                        "↓ ${formatBytes(it.networkReceiveBytesPerSec)}/s · ↑ ${formatBytes(it.networkSendBytesPerSec)}/s"
                    } ?: "whole PC",
                    values = history.network,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryTile(
    icon: ImageVector,
    tint: Color,
    label: String,
    value: String,
    caption: String,
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.height(106.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = 0.15f), tint.copy(alpha = 0.03f)),
                ),
            )
            .border(1.dp, tint.copy(alpha = 0.26f), RoundedCornerShape(16.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, 22.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TaskPalette.muted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TaskPalette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            caption,
            fontSize = 9.sp,
            color = TaskPalette.faint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Sparkline(
            values = values,
            color = tint,
            modifier = Modifier.fillMaxWidth().height(22.dp),
        )
    }
}

@Composable
private fun TaskSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = TaskPalette.text, fontSize = 15.sp),
        cursorBrush = SolidColor(TaskPalette.blue),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(shape)
                    .background(TaskPalette.chip)
                    .border(
                        1.dp,
                        if (focused) TaskPalette.blue.copy(alpha = 0.85f) else TaskPalette.border,
                        shape,
                    )
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search,
                    null,
                    tint = if (focused) TaskPalette.blue else TaskPalette.muted,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            "Search name, publisher, or path",
                            fontSize = 15.sp,
                            color = TaskPalette.faint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.size(24.dp).clip(CircleShape)
                            .background(TaskPalette.text.copy(alpha = 0.08f))
                            .clickable(onClick = onClear),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            "Clear search",
                            tint = TaskPalette.muted,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun TaskFilterRow(
    filter: TaskFilter,
    sort: TaskSort,
    onFilter: (TaskFilter) -> Unit,
    onSort: (TaskSort) -> Unit,
    onStart: () -> Unit,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) { scroll.scrollTo(0) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskFilter.entries.forEach { option ->
            GlassChip(
                selected = filter == option,
                icon = when (option) {
                    TaskFilter.ALL -> Icons.Default.GridView
                    TaskFilter.APPS -> Icons.Default.Widgets
                    TaskFilter.BACKGROUND -> Icons.Default.Layers
                },
                label = option.label,
                onClick = { onFilter(option) },
            )
        }
        SortMenu(sort, onSort)
        GlassChip(
            selected = false,
            icon = Icons.Default.Add,
            label = "Start",
            onClick = onStart,
        )
    }
}

@Composable
private fun SortMenu(current: TaskSort, onSelect: (TaskSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        GlassChip(
            selected = true,
            icon = Icons.Default.BarChart,
            label = current.label,
            onClick = { open = true },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Color(0xFF0D2447),
            border = BorderStroke(1.dp, TaskPalette.border),
            shape = RoundedCornerShape(14.dp),
        ) {
            TaskSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            color = if (option == current) TaskPalette.blue else TaskPalette.text,
                            fontWeight = if (option == current) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = {
                        if (option == current) {
                            Icon(Icons.Default.Check, null, tint = TaskPalette.blue, modifier = Modifier.size(16.dp))
                        }
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun TaskCountRow(count: Int, snapshot: WindowsTaskSnapshot?, autoRefresh: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Layers,
            null,
            tint = TaskPalette.muted,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$count ${if (count == 1) "process" else "processes"}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TaskPalette.muted,
            modifier = Modifier.weight(1f),
        )
        if (snapshot != null) {
            Icon(
                Icons.Default.Sync,
                null,
                tint = if (autoRefresh) TaskPalette.blue else TaskPalette.faint,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (autoRefresh) "Updates every 4s" else "Live updates paused",
                fontSize = 11.sp,
                color = TaskPalette.faint,
            )
        }
    }
}

@Composable
private fun TaskErrorCard(message: String, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TaskPalette.red.copy(alpha = 0.12f))
            .border(1.dp, TaskPalette.red.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Warning,
            null,
            tint = TaskPalette.red,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            message,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = TaskPalette.text,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text("Retry", color = TaskPalette.blue, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TaskRow(
    task: WindowsTask,
    icon: ImageBitmap?,
    onClick: () -> Unit,
) {
    val disk = task.diskReadBytesPerSec + task.diskWriteBytesPerSec
    GlassSurface(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            TaskAppIcon(icon, task.displayName, 44.dp, 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        task.displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TaskPalette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (task.elevated == true) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.Default.Shield,
                            "Elevated",
                            tint = TaskPalette.purple,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    if (task.protected) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.Default.Lock,
                            "Protected",
                            tint = TaskPalette.faint,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                Text(
                    "PID ${task.pid} · ${taskKind(task)}",
                    fontSize = 11.sp,
                    color = TaskPalette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetricPill(Icons.Default.Memory, formatBytes(task.workingSetBytes), TaskPalette.purple)
                    if (task.connectionCount > 0) {
                        MetricPill(Icons.Default.Language, "${task.connectionCount} conn", TaskPalette.green)
                    }
                    if (abs(disk) >= 1024) {
                        MetricPill(Icons.Default.Storage, "${formatBytes(disk)}/s", TaskPalette.blue)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    String.format(Locale.US, "%.1f%%", task.cpuPercent),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (task.cpuPercent >= 20) TaskPalette.blue else TaskPalette.text,
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    null,
                    tint = TaskPalette.faint,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun MetricPill(icon: ImageVector, text: String, tint: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            fontSize = 9.5.sp,
            color = Color(0xFFD7E6FF),
            maxLines = 1,
        )
    }
}

@Composable
private fun TaskDetailDialog(
    task: WindowsTask,
    icon: ImageBitmap?,
    history: MetricHistory,
    running: Boolean,
    onDismiss: () -> Unit,
    onEnd: () -> Unit,
    onCopy: (Boolean) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val shape = RoundedCornerShape(28.dp)
    val javaLike = task.name.contains("java", true) || task.displayName.contains("OpenJDK", true)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .heightIn(max = screenHeight * 0.96f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0E2A55), Color(0xFF081A36), Color(0xFF061226)),
                        ),
                    )
                    .border(1.dp, TaskPalette.blue.copy(alpha = 0.28f), shape),
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        TaskAppIcon(icon, task.displayName, 64.dp, 17.dp)
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                task.displayName,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = TaskPalette.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 25.sp,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "PID ${task.pid} · ${task.name}",
                                fontSize = 12.sp,
                                color = TaskPalette.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        CircleGlassButton(onClick = onDismiss, size = 36.dp) {
                            Icon(
                                Icons.Default.Close,
                                "Close details",
                                tint = TaskPalette.muted,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(11.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StatusChip(
                            color = if (running) TaskPalette.green else TaskPalette.red,
                            icon = null,
                            label = if (running) "Running" else "Ended",
                        )
                        StatusChip(
                            color = TaskPalette.blue,
                            icon = Icons.Default.Bolt,
                            label = String.format(Locale.US, "%.1f%% CPU", task.cpuPercent),
                        )
                        if (task.elevated == true) {
                            StatusChip(
                                color = TaskPalette.purple,
                                icon = Icons.Default.Shield,
                                label = "Admin",
                            )
                        }
                    }

                    Spacer(Modifier.height(13.dp))

                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            DetailMetricCard(
                                icon = Icons.Default.Speed,
                                tint = TaskPalette.blue,
                                label = "CPU",
                                value = String.format(Locale.US, "%.1f%%", task.cpuPercent),
                                caption = "Current usage",
                                values = history.cpu,
                                modifier = Modifier.weight(1f),
                            )
                            DetailMetricCard(
                                icon = Icons.Default.Memory,
                                tint = TaskPalette.purple,
                                label = "Memory",
                                value = formatBytes(task.workingSetBytes),
                                caption = "Working set",
                                values = history.memory,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            DetailMetricCard(
                                icon = Icons.Default.Storage,
                                tint = TaskPalette.green,
                                label = "Disk",
                                value = "${formatBytes(task.diskReadBytesPerSec + task.diskWriteBytesPerSec)}/s",
                                caption = "R ${formatBytes(task.diskReadBytesPerSec.toLong())}/s · " +
                                    "W ${formatBytes(task.diskWriteBytesPerSec.toLong())}/s",
                                values = history.disk,
                                modifier = Modifier.weight(1f),
                            )
                            DetailMetricCard(
                                icon = Icons.Default.Language,
                                tint = TaskPalette.orange,
                                label = "Network",
                                value = "${task.connectionCount} socket${if (task.connectionCount == 1) "" else "s"}",
                                caption = task.remoteEndpoints.firstOrNull() ?: "TCP / UDP",
                                values = history.network,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier.fillMaxWidth().height(1.dp)
                                .background(TaskPalette.borderSoft),
                        )
                        Spacer(Modifier.height(10.dp))

                        DetailLocationRow(
                            path = task.path,
                            onCopy = {
                                clipboard.setText(AnnotatedString(task.path.orEmpty()))
                                onCopy(true)
                            },
                        )
                        DetailInfoRow(
                            icon = Icons.Default.Person,
                            tint = TaskPalette.purple,
                            label = "Publisher",
                            value = task.publisher ?: "Unknown publisher",
                        )
                        DetailInfoRow(
                            icon = Icons.Default.Label,
                            tint = TaskPalette.green,
                            label = "Version",
                            value = task.version ?: "Not available",
                        )
                        DetailInfoRow(
                            icon = Icons.Default.Tune,
                            tint = TaskPalette.blue,
                            label = "Session / Threads",
                            value = "${task.sessionId} / ${task.threadCount}",
                        )
                        if (task.startedAtMs > 0) {
                            DetailInfoRow(
                                icon = Icons.Default.Schedule,
                                tint = TaskPalette.orange,
                                label = "Started",
                                value = formatDate(task.startedAtMs),
                            )
                        }
                        task.mainWindowTitle?.let { title ->
                            DetailInfoRow(
                                icon = Icons.Default.Window,
                                tint = TaskPalette.blue,
                                label = "Window",
                                value = title,
                            )
                        }
                        if (task.connectionCount > 0 && task.remoteEndpoints.isNotEmpty()) {
                            DetailInfoRow(
                                icon = Icons.Default.Language,
                                tint = TaskPalette.orange,
                                label = "Endpoints",
                                value = task.remoteEndpoints.joinToString("\n") { "→ $it" },
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        when {
                            task.protected -> DetailNote(
                                icon = Icons.Default.Shield,
                                tint = TaskPalette.red,
                                title = "Protected process",
                                body = task.protectedReason
                                    ?: "FrostLock and Windows core processes cannot be ended from a phone.",
                            )
                            javaLike -> DetailNote(
                                icon = Icons.Default.LocalCafe,
                                tint = TaskPalette.blue,
                                title = "Java runtime process",
                                body = "This is a Java runtime process (OpenJDK). It may be running an " +
                                    "application or background service.",
                            )
                            else -> DetailNote(
                                icon = Icons.Default.Info,
                                tint = TaskPalette.blue,
                                title = "Live telemetry",
                                body = "Metrics are sampled over the last 350 ms on the PC. Network shows " +
                                    "active IPv4/IPv6 TCP and UDP sockets.",
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Started, path, publisher, version and metrics come from Windows. " +
                                "Ending a program can lose unsaved work.",
                            fontSize = 9.5.sp,
                            lineHeight = 14.sp,
                            color = TaskPalette.faint,
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(
                            text = "Close",
                            icon = Icons.Default.Close,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            text = if (task.protected) "Protected" else "End task",
                            icon = if (task.protected) Icons.Default.Lock else Icons.Default.DeleteOutline,
                            enabled = !task.protected,
                            onClick = onEnd,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailMetricCard(
    icon: ImageVector,
    tint: Color,
    label: String,
    value: String,
    caption: String,
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.height(126.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = 0.16f), tint.copy(alpha = 0.03f)),
                ),
            )
            .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, 24.dp)
            Spacer(Modifier.width(7.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TaskPalette.muted)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TaskPalette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            caption,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            color = TaskPalette.faint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(24.dp),
        )
        Spacer(Modifier.weight(1f))
        Sparkline(values, tint, Modifier.fillMaxWidth().height(28.dp))
    }
}

@Composable
private fun DetailLocationRow(path: String?, onCopy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconBadge(Icons.Default.FolderOpen, TaskPalette.blue, 34.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Location",
                    fontSize = 11.sp,
                    color = TaskPalette.muted,
                    modifier = Modifier.weight(1f),
                )
                if (path != null) {
                    CircleGlassButton(
                        size = 28.dp,
                        onClick = onCopy,
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            "Copy location",
                            tint = TaskPalette.muted,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                path ?: "Not available",
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = TaskPalette.text,
            )
        }
    }
}

@Composable
private fun DetailInfoRow(
    icon: ImageVector,
    tint: Color,
    label: String,
    value: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, 34.dp)
        Spacer(Modifier.width(11.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = TaskPalette.muted,
            modifier = Modifier.width(98.dp),
        )
        Text(
            value,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            color = TaskPalette.text,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DetailNote(icon: ImageVector, tint: Color, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TaskPalette.text)
            Text(body, fontSize = 10.5.sp, lineHeight = 15.sp, color = TaskPalette.muted)
        }
    }
}

@Composable
private fun StatusChip(color: Color, icon: ImageVector?, label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(20.dp))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        } else {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun EndTaskDialog(
    task: WindowsTask,
    busy: Boolean,
    onDismiss: () -> Unit,
    onEnd: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Color(0xFF0D2447),
        titleContentColor = TaskPalette.text,
        textContentColor = TaskPalette.muted,
        iconContentColor = TaskPalette.red,
        shape = RoundedCornerShape(24.dp),
        icon = { Icon(Icons.Default.DeleteOutline, null) },
        title = { Text("End ${task.displayName}?") },
        text = {
            Text(
                "Windows will close PID ${task.pid}. Unsaved work in that program can be lost. " +
                    "If the program ignores a normal close, use Force end.",
            )
        },
        confirmButton = {
            Row {
                TextButton(enabled = !busy, onClick = { onEnd(true) }) {
                    Text("Force end", color = TaskPalette.red)
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    enabled = !busy,
                    onClick = { onEnd(false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TaskPalette.blueDeep,
                        contentColor = Color.White,
                    ),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text("End task")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text("Cancel", color = TaskPalette.muted)
            }
        },
    )
}

@Composable
private fun StartTaskDialog(
    profile: PcProfile,
    busy: Boolean,
    onDismiss: () -> Unit,
    onStart: (String, List<String>, String?) -> Unit,
) {
    var path by remember { mutableStateOf("") }
    var arguments by remember { mutableStateOf("") }
    var directory by remember { mutableStateOf("") }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val shape = RoundedCornerShape(26.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TaskPalette.text,
        unfocusedTextColor = TaskPalette.text,
        focusedBorderColor = TaskPalette.blue,
        unfocusedBorderColor = TaskPalette.border,
        focusedLabelColor = TaskPalette.blue,
        unfocusedLabelColor = TaskPalette.muted,
        cursorColor = TaskPalette.blue,
        focusedContainerColor = Color(0x330B1E3A),
        unfocusedContainerColor = Color(0x220B1E3A),
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .heightIn(max = screenHeight * 0.9f)
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0E2A55), Color(0xFF081A36), Color(0xFF061226)),
                        ),
                    )
                    .border(1.dp, TaskPalette.blue.copy(alpha = 0.28f), shape),
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Default.PlayArrow, TaskPalette.blue, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Start a program",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = TaskPalette.text,
                            )
                            Text(
                                "on ${profile.name}",
                                fontSize = 11.sp,
                                color = TaskPalette.muted,
                            )
                        }
                        CircleGlassButton(onClick = onDismiss, size = 34.dp) {
                            Icon(
                                Icons.Default.Close,
                                "Close",
                                tint = TaskPalette.muted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it.take(4096) },
                        label = { Text("Full program path") },
                        placeholder = { Text("C:\\Windows\\System32\\notepad.exe") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = arguments,
                        onValueChange = { arguments = it.take(2048) },
                        label = { Text("Arguments (optional)") },
                        placeholder = { Text("Use quotes for spaces: \"C:\\My Files\\test.txt\"") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = directory,
                        onValueChange = { directory = it.take(4096) },
                        label = { Text("Working directory (optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(12.dp))
                    DetailNote(
                        icon = Icons.Default.Info,
                        tint = TaskPalette.blue,
                        title = "Runs on the PC",
                        body = "Use a full path that exists on the PC. Phones may start .exe, .bat, .cmd, " +
                            "and .com programs in your signed-in Windows session.",
                    )

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(
                            text = "Cancel",
                            icon = Icons.Default.Close,
                            onClick = onDismiss,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            text = if (busy) "Starting…" else "Start",
                            icon = Icons.Default.PlayArrow,
                            enabled = path.isNotBlank() && !busy,
                            onClick = {
                                onStart(
                                    path.trim(),
                                    splitArguments(arguments),
                                    directory.trim().ifBlank { null },
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.height(50.dp)
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.horizontalGradient(listOf(TaskPalette.blueDeep, Color(0xFF57B8FF)))
                } else {
                    SolidColor(Color(0xFF10294B))
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                tint = if (enabled) Color(0xFF06142B) else TaskPalette.faint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color(0xFF06142B) else TaskPalette.faint,
            )
        }
    }
}

@Composable
private fun SecondaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.height(50.dp)
            .clip(shape)
            .background(Color(0x1F7DA9E8))
            .border(1.dp, TaskPalette.border, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                tint = TaskPalette.text,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TaskPalette.text,
            )
        }
    }
}

@Composable
private fun GlassChip(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(21.dp)
    Row(
        Modifier.clip(shape)
            .background(
                if (selected) {
                    Brush.horizontalGradient(listOf(Color(0xFF2E7CF5), Color(0xFF57B8FF)))
                } else {
                    SolidColor(TaskPalette.chip)
                },
            )
            .border(
                1.dp,
                if (selected) Color.Transparent else TaskPalette.border,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            tint = if (selected) Color(0xFF06142B) else TaskPalette.blue,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color(0xFF06142B) else TaskPalette.text,
        )
    }
}

@Composable
private fun GlassPill(
    onClick: () -> Unit,
    borderColor: Color,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.clip(RoundedCornerShape(18.dp))
            .background(TaskPalette.chip)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun CircleGlassButton(
    onClick: () -> Unit,
    size: Dp = 42.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.size(size)
            .clip(CircleShape)
            .background(Color(0x1F6FA9EE))
            .border(1.dp, TaskPalette.border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color, size: Dp) {
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        Modifier.size(size)
            .clip(shape)
            .background(tint.copy(alpha = 0.15f))
            .border(1.dp, tint.copy(alpha = 0.25f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

@Composable
private fun TaskAppIcon(icon: ImageBitmap?, name: String, size: Dp, radius: Dp) {
    val shape = RoundedCornerShape(radius)
    Box(
        Modifier.size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF173C6B), Color(0xFF0B2346))))
            .border(1.dp, TaskPalette.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(size * 0.11f),
            )
        } else {
            Text(
                name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.Bold,
                color = TaskPalette.blue,
            )
        }
    }
}

@Composable
private fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.clip(shape)
            .background(TaskGlassBrush)
            .border(1.dp, TaskPalette.border, shape),
        content = content,
    )
}

@Composable
private fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val data = if (values.size == 1) listOf(values[0], values[0]) else values
        val rawMin = data.minOrNull() ?: 0f
        val rawMax = data.maxOrNull() ?: 0f
        val pad = max((rawMax - rawMin) * 0.18f, 0.6f)
        val min = rawMin - pad
        val max = rawMax + pad
        val span = (max - min).takeIf { it > 0.0001f } ?: 1f
        val stepX = size.width / (data.size - 1)
        fun y(value: Float): Float {
            val norm = ((value - min) / span).coerceIn(0f, 1f)
            return size.height - 3.dp.toPx() - norm * (size.height - 6.dp.toPx())
        }

        val line = Path().apply {
            moveTo(0f, y(data.first()))
            data.forEachIndexed { index, value -> lineTo(index * stepX, y(value)) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.34f), color.copy(alpha = 0.01f)),
            ),
        )
        drawPath(
            path = line,
            color = color.copy(alpha = 0.22f),
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(
            color = color,
            radius = 2.2.dp.toPx(),
            center = Offset(size.width, y(data.last())),
        )
    }
}

private fun TaskPalette.tertiaryBorder(): Color = purple.copy(alpha = 0.35f)

private fun taskKind(task: WindowsTask): String = when {
    task.hasWindow -> "App"
    task.sessionId == 0 -> "Service"
    task.protected -> "System"
    else -> "Background"
}

private fun splitArguments(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var index = 0
    while (index < raw.length) {
        val character = raw[index]
        when {
            character == '"' -> quoted = !quoted
            character.isWhitespace() && !quoted -> {
                if (current.isNotEmpty()) {
                    result += current.toString()
                    current.clear()
                }
            }
            else -> current.append(character)
        }
        index++
    }
    if (current.isNotEmpty()) result += current.toString()
    return result.take(32)
}

private fun formatBytes(value: Double): String = formatBytes(value.toLong())

private fun formatBytes(value: Long): String {
    if (value <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = value.toDouble()
    var unit = 0
    while (size >= 1024 && unit < units.lastIndex) {
        size /= 1024
        unit++
    }
    return if (unit == 0) "${size.toInt()} B"
    else String.format(Locale.US, "%.1f %s", size, units[unit])
}

private fun formatDate(milliseconds: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(milliseconds))
