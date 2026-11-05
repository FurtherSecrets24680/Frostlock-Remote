package com.frostlock.remote.ui

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
import com.frostlock.remote.data.TaskUnits
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

/**
 * One row in the process list. A group stands for every process that shares a display
 * name, so the collapsed row can show combined totals instead of duplicate entries.
 */
private data class ProcessEntry(
    val key: String,
    val label: String,
    val tasks: List<WindowsTask>,
    val isGroup: Boolean,
) {
    val cpuPercent: Double get() = tasks.sumOf { it.cpuPercent }
    val workingSetBytes: Long get() = tasks.sumOf { it.workingSetBytes }
    val diskBytesPerSec: Double get() = tasks.sumOf { it.diskReadBytesPerSec + it.diskWriteBytesPerSec }
    val connectionCount: Int get() = tasks.sumOf { it.connectionCount }

    companion object {
        fun single(task: WindowsTask) = ProcessEntry(
            key = "pid-${task.pid}",
            label = task.displayName,
            tasks = listOf(task),
            isGroup = false,
        )

        fun group(label: String, tasks: List<WindowsTask>) = ProcessEntry(
            key = "group-${label.lowercase(Locale.getDefault())}",
            label = label,
            tasks = tasks,
            isGroup = tasks.size > 1,
        )
    }
}

private object TaskPalette {
    @Composable private fun dark() = MaterialTheme.colorScheme.background.luminance() < 0.5f
    @get:Composable val cardDeep get() = if (dark()) Color(0xFF172631) else Color(0xFFF7F9FA)
    @get:Composable val border get() = MaterialTheme.colorScheme.outline.copy(alpha = if (dark()) 0.55f else 0.75f)
    @get:Composable val borderSoft get() = MaterialTheme.colorScheme.outlineVariant
    @get:Composable val text get() = MaterialTheme.colorScheme.onSurface
    @get:Composable val muted get() = MaterialTheme.colorScheme.onSurfaceVariant
    @get:Composable val faint get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    @get:Composable val blue get() = MaterialTheme.colorScheme.primary
    @get:Composable val blueDeep get() = if (dark()) Color(0xFF2E7CF5) else Color(0xFF147DAB)
    @get:Composable val purple get() = if (dark()) Color(0xFFB28CFF) else Color(0xFF7250A8)
    @get:Composable val green get() = MaterialTheme.colorScheme.tertiary
    @get:Composable val orange get() = if (dark()) Color(0xFFFFB454) else Color(0xFF9A5B00)
    @get:Composable val red get() = MaterialTheme.colorScheme.error
    @get:Composable val chip get() = if (dark()) Color(0x1A7DA9E8) else Color(0xFFE7F1F6)
}

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
    taskUnits: TaskUnits,
    groupProcesses: Boolean,
    refreshSeconds: Int,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<WindowsTaskSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var autoRefresh by remember { mutableStateOf(refreshSeconds > 0) }
    var processPage by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(TaskSort.CPU) }
    var filter by remember { mutableStateOf(TaskFilter.ALL) }
    var selected by remember { mutableStateOf<WindowsTask?>(null) }
    var pendingKill by remember { mutableStateOf<WindowsTask?>(null) }
    var pendingRestart by remember { mutableStateOf<WindowsTask?>(null) }
    var startOpen by remember { mutableStateOf(false) }
    var actionBusy by remember { mutableStateOf(false) }
    var systemHistory by remember { mutableStateOf(SystemHistory()) }
    val processHistory = remember { mutableStateMapOf<Int, MetricHistory>() }
    val icons = remember { mutableStateMapOf<String, ImageBitmap>() }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    TaskSystemBars()
    BackHandler(enabled = processPage) { processPage = false }

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

    fun restartTask(task: WindowsTask) {
        val path = task.path ?: return
        if (actionBusy) return
        actionBusy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.endTask(profile, task.pid, force = false)
                    api.startTask(profile, path)
                }
            }.onSuccess {
                pendingRestart = null
                selected = null
                onToast("Restarted ${task.displayName} on ${profile.name}.")
                loadSnapshot(false)
            }.onFailure {
                onToast(it.message ?: "FrostLock could not restart that task.")
            }
            actionBusy = false
        }
    }

    LaunchedEffect(profile.id) { loadSnapshot(true) }
    LaunchedEffect(refreshSeconds) {
        if (refreshSeconds > 0) autoRefresh = true
    }
    LaunchedEffect(profile.id, autoRefresh, refreshSeconds) {
        if (!autoRefresh || refreshSeconds <= 0) return@LaunchedEffect
        while (true) {
            delay(refreshSeconds * 1000L)
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

    val entries = remember(snapshot, search, sort, filter, groupProcesses) {
        val query = search.trim()
        val filtered = snapshot?.processes.orEmpty()
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

        val grouped = if (groupProcesses) {
            filtered.groupBy { it.displayName.trim().lowercase(Locale.getDefault()) }
                .map { (_, tasks) ->
                    if (tasks.size == 1) ProcessEntry.single(tasks.first())
                    else ProcessEntry.group(tasks.first().displayName, tasks)
                }
        } else {
            filtered.map(ProcessEntry::single)
        }

        grouped.sortedWith(
            when (sort) {
                TaskSort.CPU -> compareByDescending<ProcessEntry> { it.cpuPercent }
                    .thenByDescending { it.workingSetBytes }
                TaskSort.MEMORY -> compareByDescending { it.workingSetBytes }
                TaskSort.DISK -> compareByDescending { it.diskBytesPerSec }
                TaskSort.NETWORK -> compareByDescending { it.connectionCount }
                TaskSort.NAME -> compareBy { it.label.lowercase(Locale.getDefault()) }
            },
        )
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Scaffold(containerColor = Color.Transparent) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    TaskTopBar(
                        title = if (processPage) "Processes" else "Task manager",
                        machine = snapshot?.machineName ?: profile.name,
                        live = autoRefresh,
                        refreshing = refreshing,
                        onBack = { if (processPage) processPage = false else onBack() },
                        onToggleLive = { autoRefresh = !autoRefresh },
                        onRefresh = { scope.launch { loadSnapshot(true) } },
                    )

                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (!processPage) {
                            item(key = "summary") {
                                MachineCard(
                                    snapshot = snapshot,
                                    history = systemHistory,
                                    units = taskUnits,
                                    connected = snapshot != null && error == null,
                                    connecting = snapshot == null && error == null,
                                )
                            }
                            item(key = "manage-processes") {
                                ManageProcessesCard(
                                    count = snapshot?.processCount,
                                    onClick = { processPage = true },
                                )
                            }
                        } else {
                            item(key = "search") {
                                ProcessSearchRow(
                                    value = search,
                                    onValueChange = { search = it.take(80) },
                                    onClear = { search = "" },
                                    onStart = { startOpen = true },
                                )
                            }
                            item(key = "filters") {
                                TaskFilterRow(
                                    filter = filter,
                                    sort = sort,
                                    onFilter = { filter = it },
                                    onSort = { sort = it },
                                )
                            }
                        }

                        if (error != null) {
                            item(key = "error") {
                                TaskErrorCard(error.orEmpty()) {
                                    scope.launch { loadSnapshot(true) }
                                }
                            }
                        }

                        if (processPage) {
                            item(key = "count") {
                                TaskCountRow(
                                    count = entries.sumOf { it.tasks.size },
                                    snapshot = snapshot,
                                    autoRefresh = autoRefresh,
                                    refreshSeconds = refreshSeconds,
                                )
                            }
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

                        if (processPage) {
                            items(entries, key = { it.key }) { entry ->
                                if (!entry.isGroup) {
                                    val task = entry.tasks.first()
                                    TaskRow(
                                        task = task,
                                        icon = task.iconKey?.let(icons::get),
                                        units = taskUnits,
                                        onClick = { selected = task },
                                        restart = restartable(task),
                                        onRestart = { pendingRestart = task },
                                        onEnd = { pendingKill = task },
                                        onCopy = { copied ->
                                            onToast(if (copied) "Location copied." else "Nothing to copy.")
                                        },
                                    )
                                } else {
                                    val expanded = expandedGroups[entry.key] == true
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TaskGroupRow(
                                            entry = entry,
                                            icon = entry.tasks.firstNotNullOfOrNull { it.iconKey }?.let(icons::get),
                                            units = taskUnits,
                                            expanded = expanded,
                                            onToggle = { expandedGroups[entry.key] = !expanded },
                                            onClick = { selected = entry.tasks.first() },
                                            onEndTop = { pendingKill = entry.tasks.first() },
                                        )
                                        if (expanded) {
                                            entry.tasks.forEach { task ->
                                                TaskRow(
                                                    task = task,
                                                    icon = task.iconKey?.let(icons::get),
                                                    units = taskUnits,
                                                    modifier = Modifier.padding(start = 14.dp),
                                                    onClick = { selected = task },
                                                    restart = restartable(task),
                                                    onRestart = { pendingRestart = task },
                                                    onEnd = { pendingKill = task },
                                                    onCopy = { copied ->
                                                        onToast(if (copied) "Location copied." else "Nothing to copy.")
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
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
                units = taskUnits,
                running = running,
                onDismiss = { selected = null },
                onRestart = {
                    selected = null
                    pendingRestart = live
                },
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

        pendingRestart?.let { task ->
            RestartTaskDialog(
                task = task,
                busy = actionBusy,
                onDismiss = { if (!actionBusy) pendingRestart = null },
                onRestart = { restartTask(task) },
            )
        }

        if (startOpen) {
            RunCommandDialog(
                profile = profile,
                busy = actionBusy,
                onDismiss = { if (!actionBusy) startOpen = false },
                onRun = { command ->
                    actionBusy = true
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) { api.runCommand(profile, command) }
                        }.onSuccess {
                            startOpen = false
                            onToast("Sent the command to ${profile.name}.")
                            loadSnapshot(false)
                        }.onFailure {
                            onToast(it.message ?: "FrostLock could not run that command on the PC.")
                        }
                        actionBusy = false
                    }
                },
            )
        }
}

@Composable
private fun TaskSystemBars() {
    val view = LocalView.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val barColor = if (isDark) 0xFF0B141B else 0xFFF5F8FA
    DisposableEffect(isDark) {
        val window = (view.context as? Activity)?.window
            ?: return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        val previousLightStatus = controller.isAppearanceLightStatusBars
        val previousLightNavigation = controller.isAppearanceLightNavigationBars
        @Suppress("DEPRECATION") val previousStatus = window.statusBarColor
        @Suppress("DEPRECATION") val previousNavigation = window.navigationBarColor
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
        @Suppress("DEPRECATION") window.statusBarColor = barColor.toInt()
        @Suppress("DEPRECATION") window.navigationBarColor = barColor.toInt()
        onDispose {
            controller.isAppearanceLightStatusBars = previousLightStatus
            controller.isAppearanceLightNavigationBars = previousLightNavigation
            @Suppress("DEPRECATION") window.statusBarColor = previousStatus
            @Suppress("DEPRECATION") window.navigationBarColor = previousNavigation
        }
    }
}

@Composable
private fun TaskTopBar(
    title: String,
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
                title,
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
        IconButton(onClick = onToggleLive) {
            Icon(
                if (live) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (live) "Pause live updates" else "Resume live updates",
                tint = TaskPalette.blue,
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
    units: TaskUnits,
    connected: Boolean,
    connecting: Boolean,
) {
    GlassSurface(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.GridView,
                        null,
                        tint = TaskPalette.blue,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, tint = TaskPalette.green, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Admin", fontSize = 11.sp, color = TaskPalette.muted)
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
                    value = snapshot?.let { formatBytes(it.usedMemoryBytes, units) } ?: "—",
                    caption = snapshot?.let { "of ${formatBytes(it.totalMemoryBytes, units)}" } ?: "physical RAM",
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
                    caption = snapshot?.let { "${it.appCount} apps, ${it.connectionCount} sockets" } ?: "Windows tasks",
                    values = history.processes,
                    modifier = Modifier.weight(1f),
                )
                SummaryTile(
                    icon = Icons.Default.Language,
                    tint = TaskPalette.orange,
                    label = "Network",
                    value = snapshot?.let {
                        "${formatBytes(it.networkReceiveBytesPerSec + it.networkSendBytesPerSec, units)}/s"
                    } ?: "—",
                    caption = snapshot?.let {
                        "↓ ${formatBytes(it.networkReceiveBytesPerSec, units)}/s  ↑ ${formatBytes(it.networkSendBytesPerSec, units)}/s"
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, TaskPalette.border, RoundedCornerShape(16.dp))
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
            fontSize = 11.sp,
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
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = TaskPalette.text, fontSize = 15.sp),
        cursorBrush = SolidColor(TaskPalette.blue),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
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
                            "Search processes",
                            fontSize = 14.sp,
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
private fun ProcessSearchRow(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onStart: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskSearchField(
            value = value,
            onValueChange = onValueChange,
            onClear = onClear,
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(
            onClick = onStart,
            modifier = Modifier.size(50.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Run a command")
        }
    }
}

@Composable
private fun ManageProcessesCard(count: Int?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, TaskPalette.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Default.Layers, TaskPalette.blue, 40.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Manage processes",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TaskPalette.text,
            )
            Text(
                count?.let { "$it running. Inspect or end a task." } ?: "View, inspect, or end running tasks",
                fontSize = 12.sp,
                color = TaskPalette.muted,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = TaskPalette.muted,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = 180f },
        )
    }
}

@Composable
private fun TaskFilterRow(
    filter: TaskFilter,
    sort: TaskSort,
    onFilter: (TaskFilter) -> Unit,
    onSort: (TaskSort) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskFilter.entries.forEach { option ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = { onFilter(option) }) {
                    Text(
                        option.label,
                        color = if (filter == option) TaskPalette.blue else TaskPalette.muted,
                        fontWeight = if (filter == option) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
                Box(
                    Modifier.width(28.dp).height(2.dp)
                        .background(if (filter == option) TaskPalette.blue else Color.Transparent),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        SortMenu(sort, onSort)
    }
}

@Composable
private fun SortMenu(current: TaskSort, onSelect: (TaskSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text("Sort: ${current.label}", color = TaskPalette.blue, maxLines = 1)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = MaterialTheme.colorScheme.surface,
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
private fun TaskCountRow(count: Int, snapshot: WindowsTaskSnapshot?, autoRefresh: Boolean, refreshSeconds: Int) {
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
                if (autoRefresh) "Updates every ${refreshSeconds}s" else "Live updates paused",
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
    units: TaskUnits,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    restart: Boolean,
    onRestart: () -> Unit,
    onEnd: () -> Unit,
    onCopy: (Boolean) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    val disk = task.diskReadBytesPerSec + task.diskWriteBytesPerSec
    GlassSurface(
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 11.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProcessIdentityChip("PID ${task.pid}", TaskPalette.blue)
                    ProcessIdentityChip(if (task.hasWindow) "App" else "Background", if (task.hasWindow) TaskPalette.green else TaskPalette.orange)
                }
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProcessMetric(Icons.Default.Speed, TaskPalette.blue, "CPU", String.format(Locale.US, "%.1f%%", task.cpuPercent))
                    ProcessMetric(Icons.Default.Memory, TaskPalette.purple, "Memory", formatBytes(task.workingSetBytes, units))
                    ProcessMetric(Icons.Default.Language, TaskPalette.orange, "Connections", task.connectionCount.toString())
                    if (abs(disk) >= 1024) ProcessMetric(Icons.Default.Storage, TaskPalette.green, "Disk", "${formatBytes(disk, units)}/s")
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        "Actions for ${task.displayName}",
                        tint = TaskPalette.muted,
                        modifier = Modifier.size(19.dp),
                    )
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    border = BorderStroke(1.dp, TaskPalette.border),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("View details") },
                        leadingIcon = { Icon(Icons.Default.Info, null, tint = TaskPalette.blue) },
                        onClick = { menu = false; onClick() },
                    )
                    if (restart) {
                        DropdownMenuItem(
                            text = { Text("Restart") },
                            leadingIcon = { Icon(Icons.Default.RestartAlt, null, tint = TaskPalette.green) },
                            onClick = { menu = false; onRestart() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Copy location") },
                        enabled = task.path != null,
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = TaskPalette.muted) },
                        onClick = {
                            menu = false
                            if (task.path != null) {
                                clipboard.setText(AnnotatedString(task.path))
                                onCopy(true)
                            } else {
                                onCopy(false)
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("End task", color = TaskPalette.red) },
                        enabled = !task.protected,
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = TaskPalette.red) },
                        onClick = { menu = false; onEnd() },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskGroupRow(
    entry: ProcessEntry,
    icon: ImageBitmap?,
    units: TaskUnits,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onEndTop: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val disk = entry.diskBytesPerSec
    val label = "${entry.label} (${entry.tasks.size})"
    GlassSurface(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 11.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    if (expanded) "Collapse $label" else "Expand $label",
                    tint = TaskPalette.muted,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            TaskAppIcon(icon, entry.label, 40.dp, 11.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TaskPalette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProcessIdentityChip("${entry.tasks.size} processes", TaskPalette.blue)
                    ProcessIdentityChip(
                        if (entry.tasks.any { it.hasWindow }) "App" else "Background",
                        if (entry.tasks.any { it.hasWindow }) TaskPalette.green else TaskPalette.orange,
                    )
                }
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProcessMetric(Icons.Default.Speed, TaskPalette.blue, "CPU", String.format(Locale.US, "%.1f%%", entry.cpuPercent))
                    ProcessMetric(Icons.Default.Memory, TaskPalette.purple, "Memory", formatBytes(entry.workingSetBytes, units))
                    ProcessMetric(Icons.Default.Language, TaskPalette.orange, "Connections", entry.connectionCount.toString())
                    if (abs(disk) >= 1024) ProcessMetric(Icons.Default.Storage, TaskPalette.green, "Disk", "${formatBytes(disk, units)}/s")
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        "Actions for $label",
                        tint = TaskPalette.muted,
                        modifier = Modifier.size(19.dp),
                    )
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    border = BorderStroke(1.dp, TaskPalette.border),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text(if (expanded) "Collapse" else "Expand") },
                        leadingIcon = {
                            Icon(
                                if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                null,
                                tint = TaskPalette.blue,
                            )
                        },
                        onClick = { menu = false; onToggle() },
                    )
                    DropdownMenuItem(
                        text = { Text("View top process") },
                        leadingIcon = { Icon(Icons.Default.Info, null, tint = TaskPalette.blue) },
                        onClick = { menu = false; onClick() },
                    )
                    DropdownMenuItem(
                        text = { Text("End top task", color = TaskPalette.red) },
                        enabled = !entry.tasks.first().protected,
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = TaskPalette.red) },
                        onClick = { menu = false; onEndTop() },
                    )
                }
            }
        }
    }
}

private fun restartable(task: WindowsTask): Boolean {
    if (task.protected || task.path == null) return false
    if (task.name.contains("java", ignoreCase = true) || task.displayName.contains("OpenJDK", ignoreCase = true)) return false
    val extension = task.path.substringAfterLast('.', "").lowercase(Locale.getDefault())
    return extension in setOf("exe", "bat", "cmd", "com")
}

@Composable
private fun ProcessIdentityChip(text: String, color: Color) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
    )
}

@Composable
private fun ProcessMetric(icon: ImageVector, tint: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, "$label: $value", tint = tint, modifier = Modifier.size(13.dp))
        Text(value, fontSize = 10.sp, color = TaskPalette.text, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

@Composable
private fun TaskDetailDialog(
    task: WindowsTask,
    icon: ImageBitmap?,
    history: MetricHistory,
    units: TaskUnits,
    running: Boolean,
    onDismiss: () -> Unit,
    onRestart: () -> Unit,
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
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, TaskPalette.border, shape),
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
                                "PID ${task.pid}    ${task.name}",
                                fontSize = 12.sp,
                                color = TaskPalette.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        CircleGlassButton(onClick = onDismiss, size = 36.dp) {
                            Box(
                                Modifier.fillMaxSize().clip(CircleShape)
                                    .background(TaskPalette.red.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Close, "Close details", tint = TaskPalette.red, modifier = Modifier.size(17.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(11.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(if (running) TaskPalette.green else TaskPalette.red),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(if (running) "Running" else "Ended", fontSize = 12.sp, color = TaskPalette.muted)
                        if (task.elevated == true) {
                            Spacer(Modifier.width(14.dp))
                            Icon(Icons.Default.Shield, null, tint = TaskPalette.muted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Admin", fontSize = 12.sp, color = TaskPalette.muted)
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
                                value = formatBytes(task.workingSetBytes, units),
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
                                value = "${formatBytes(task.diskReadBytesPerSec + task.diskWriteBytesPerSec, units)}/s",
                                caption = "Read ${formatBytes(task.diskReadBytesPerSec.toLong(), units)}/s  |  " +
                                    "Write ${formatBytes(task.diskWriteBytesPerSec.toLong(), units)}/s",
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
                        else -> Unit
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
                        if (restartable(task)) {
                            PrimaryButton(
                                text = "Restart task",
                                icon = Icons.Default.RestartAlt,
                                enabled = !task.protected,
                                onClick = onRestart,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        PrimaryButton(
                            text = if (task.protected) "Protected" else "End task",
                            icon = if (task.protected) Icons.Default.Lock else Icons.Default.DeleteOutline,
                            enabled = !task.protected,
                            onClick = onEnd,
                            modifier = Modifier.weight(1f),
                            destructive = true,
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, TaskPalette.border, RoundedCornerShape(16.dp))
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
            fontSize = 11.sp,
            lineHeight = 13.sp,
            color = TaskPalette.faint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(28.dp),
        )
        Spacer(Modifier.weight(1f))
        Sparkline(values, tint, Modifier.fillMaxWidth().height(28.dp))
    }
}

@Composable
private fun DetailLocationRow(path: String?, onCopy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(10.dp)).background(TaskPalette.cardDeep)
            .border(1.dp, TaskPalette.border, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Default.FolderOpen, null, tint = TaskPalette.blue, modifier = Modifier.size(20.dp))
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
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(10.dp)).background(TaskPalette.cardDeep)
            .border(1.dp, TaskPalette.border, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(11.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = TaskPalette.muted,
            maxLines = 2,
            modifier = Modifier.weight(0.38f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            color = TaskPalette.text,
            modifier = Modifier.weight(0.62f),
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
            Text(body, fontSize = 11.sp, lineHeight = 15.sp, color = TaskPalette.muted)
        }
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
        containerColor = MaterialTheme.colorScheme.surface,
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
                        containerColor = TaskPalette.red,
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
private fun RestartTaskDialog(
    task: WindowsTask,
    busy: Boolean,
    onDismiss: () -> Unit,
    onRestart: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = TaskPalette.text,
        textContentColor = TaskPalette.muted,
        iconContentColor = TaskPalette.green,
        shape = RoundedCornerShape(24.dp),
        icon = { Icon(Icons.Default.RestartAlt, null) },
        title = { Text("Restart ${task.displayName}?") },
        text = {
            Text(
                "Windows will close PID ${task.pid} and start it again from the same location. " +
                    "Unsaved work in that program can be lost.",
            )
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = onRestart,
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
                    Text("Restart task")
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
private fun RunCommandDialog(
    profile: PcProfile,
    busy: Boolean,
    onDismiss: () -> Unit,
    onRun: (String) -> Unit,
) {
    var command by remember { mutableStateOf("") }
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
        focusedContainerColor = TaskPalette.chip,
        unfocusedContainerColor = TaskPalette.chip.copy(alpha = 0.55f),
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
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, TaskPalette.border, shape),
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Default.PlayArrow, TaskPalette.blue, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Run a command",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = TaskPalette.text,
                            )
                            Text(
                                profile.name,
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
                        value = command,
                        onValueChange = { command = it.take(500) },
                        label = { Text("Windows command") },
                        placeholder = { Text("notepad.exe   |   ipconfig /all   |   shutdown /r /t 0") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Runs with cmd.exe in your signed-in Windows session on ${profile.name}. " +
                            "Output stays on the PC.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = TaskPalette.muted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(
                            text = if (busy) "Running…" else "Run",
                            icon = Icons.Default.PlayArrow,
                            enabled = command.isNotBlank() && !busy,
                            onClick = { onRun(command.trim()) },
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
    destructive: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.height(50.dp)
            .clip(shape)
            .background(
                SolidColor(
                    when {
                        !enabled -> MaterialTheme.colorScheme.surfaceVariant
                        destructive -> TaskPalette.red
                        else -> TaskPalette.blueDeep
                    },
                ),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                tint = if (enabled) MaterialTheme.colorScheme.onPrimary else TaskPalette.faint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onPrimary else TaskPalette.faint,
            )
        }
    }
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
            .background(TaskPalette.chip)
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
            .background(TaskPalette.cardDeep)
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
            .background(MaterialTheme.colorScheme.surface)
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

@Composable
private fun TaskPalette.tertiaryBorder(): Color = purple.copy(alpha = 0.35f)

private fun taskKind(task: WindowsTask): String = when {
    task.hasWindow -> "App"
    task.sessionId == 0 -> "Service"
    task.protected -> "System"
    else -> "Background"
}

private fun formatBytes(value: Double, units: TaskUnits): String = formatBytes(value.toLong(), units)

private fun formatBytes(value: Long, units: TaskUnits): String {
    if (value <= 0) return "0 B"
    val steps = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = value.toDouble()
    var unit = 0
    while (size >= 1024 && unit < steps.lastIndex) {
        size /= 1024
        unit++
    }
    val target = when (units) {
        TaskUnits.AUTO -> unit
        TaskUnits.MB -> 2
        TaskUnits.GB -> 3
    }
    while (unit < target) {
        size /= 1024
        unit++
    }
    while (unit > target) {
        size *= 1024
        unit--
    }
    return if (unit == 0) "${size.toInt()} B"
    else String.format(Locale.US, "%.1f %s", size, steps[unit])
}

private fun formatDate(milliseconds: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(milliseconds))
