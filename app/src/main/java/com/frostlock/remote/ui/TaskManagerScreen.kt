package com.frostlock.remote.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun TaskManagerScreen(
    api: FrostLockApi,
    profile: PcProfile,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
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
    val icons = remember { mutableStateMapOf<String, ImageBitmap>() }

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
                    TaskSort.DISK -> compareByDescending<WindowsTask> { it.diskReadBytesPerSec + it.diskWriteBytesPerSec }
                    TaskSort.NETWORK -> compareByDescending<WindowsTask> { it.connectionCount }
                    TaskSort.NAME -> compareBy { it.displayName.lowercase(Locale.getDefault()) }
                },
            )
    }

    Scaffold(containerColor = colors.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, end = 10.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to ${profile.name}", tint = colors.onBackground)
                }
                Column(Modifier.weight(1f)) {
                    Text("Task manager", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = colors.onBackground)
                    Text(
                        profile.name,
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { autoRefresh = !autoRefresh }) {
                    Text(if (autoRefresh) "Pause live" else "Resume live")
                }
                IconButton(
                    onClick = { scope.launch { loadSnapshot(true) } },
                    enabled = !refreshing,
                ) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, "Refresh tasks", tint = colors.onBackground)
                }
            }

            SummaryCard(snapshot, Modifier.padding(horizontal = 16.dp, vertical = 10.dp))

            OutlinedTextField(
                value = search,
                onValueChange = { search = it.take(80) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search name, publisher, or path") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Clear search") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )

            Row(
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.label) },
                    )
                }
                Spacer(Modifier.width(4.dp))
                SortMenu(sort) { sort = it }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = { startOpen = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Start task", fontSize = 12.sp)
                }
            }

            if (error != null) {
                Surface(
                    color = colors.errorContainer,
                    contentColor = colors.onErrorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        WarningIcon(colors.onErrorContainer)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            error.orEmpty(),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { scope.launch { loadSnapshot(true) } }) { Text("Retry") }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${processes.size} ${if (processes.size == 1) "process" else "processes"}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (snapshot != null) {
                    Text(
                        if (autoRefresh) "Updates every 4s" else "Live updates paused",
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (snapshot == null && refreshing) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
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

    selected?.let { task ->
        TaskDetailDialog(
            task = task,
            onDismiss = { selected = null },
            onEnd = { pendingKill = task },
        )
    }

    pendingKill?.let { task ->
        AlertDialog(
            onDismissRequest = { if (!actionBusy) pendingKill = null },
            title = { Text("End ${task.displayName}?") },
            text = {
                Text(
                    "Windows will close PID ${task.pid}. Unsaved work in that program can be lost. " +
                        "If the program ignores a normal close, use Force end.",
                )
            },
            confirmButton = {
                Row {
                    TextButton(enabled = !actionBusy, onClick = { endTask(task, force = true) }) {
                        Text("Force end", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(enabled = !actionBusy, onClick = { endTask(task, force = false) }) {
                        Text("End task")
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !actionBusy, onClick = { pendingKill = null }) { Text("Cancel") }
            },
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

@Composable
private fun SummaryCard(snapshot: WindowsTaskSnapshot?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    snapshot?.machineName ?: "Windows PC",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                    modifier = Modifier.weight(1f),
                )
                if (snapshot?.controllerElevated == true) {
                    Icon(Icons.Default.Lock, "FrostLock is running as administrator", Modifier.size(14.dp), tint = colors.tertiary)
                    Spacer(Modifier.width(4.dp))
                    Text("Admin", fontSize = 10.sp, color = colors.tertiary)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetric(
                    "CPU",
                    snapshot?.let { String.format(Locale.US, "%.1f%%", it.totalCpuPercent) } ?: "—",
                    "system total",
                    Modifier.weight(1f),
                )
                SummaryMetric(
                    "MEMORY",
                    snapshot?.let { formatBytes(it.usedMemoryBytes) } ?: "—",
                    snapshot?.let { "of ${formatBytes(it.totalMemoryBytes)}" } ?: "physical RAM",
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetric(
                    "PROCESSES",
                    snapshot?.processCount?.toString() ?: "—",
                    snapshot?.let { "${it.appCount} apps · ${it.connectionCount} sockets" } ?: "Windows tasks",
                    Modifier.weight(1f),
                )
                SummaryMetric(
                    "NETWORK",
                    snapshot?.let { "${formatBytes(it.networkReceiveBytesPerSec)}/s" } ?: "—",
                    snapshot?.let { "up ${formatBytes(it.networkSendBytesPerSec)}/s · whole PC" } ?: "whole PC",
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, caption: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Text(label, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1)
        Text(caption, fontSize = 10.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SortMenu(current: TaskSort, onSelect: (TaskSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)) {
            Icon(Icons.Default.Sort, null, Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(current.label, fontSize = 12.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TaskSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(if (option == current) "✓ ${option.label}" else option.label) },
                    onClick = { open = false; onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: WindowsTask,
    icon: ImageBitmap?,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)),
                )
            } else {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(colors.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        task.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        fontSize = 16.sp,
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        task.displayName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (task.protected) {
                        Icon(Icons.Default.Lock, "Protected", Modifier.size(13.dp), tint = colors.onSurfaceVariant)
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        String.format(Locale.US, "%.1f%%", task.cpuPercent),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (task.cpuPercent >= 20) colors.primary else colors.onSurfaceVariant,
                    )
                }
                Text(
                    "PID ${task.pid} · ${task.publisher ?: if (task.hasWindow) "App" else "Background"}",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetricPill("RAM", formatBytes(task.workingSetBytes))
                    val disk = task.diskReadBytesPerSec + task.diskWriteBytesPerSec
                    if (abs(disk) >= 1024) MetricPill("Disk", "${formatBytes(disk)}/s")
                    if (task.connectionCount > 0) MetricPill("Net", "${task.connectionCount} conn")
                }
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surfaceVariant,
        shape = RoundedCornerShape(7.dp),
    ) {
        Text(
            "$label $value",
            fontSize = 10.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun TaskDetailDialog(
    task: WindowsTask,
    onDismiss: () -> Unit,
    onEnd: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(task.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("PID ${task.pid} · ${task.name}", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        },
        text = {
            Column(
                Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                DetailRow("CPU", String.format(Locale.US, "%.1f%%", task.cpuPercent))
                DetailRow("Memory (working set)", formatBytes(task.workingSetBytes))
                DetailRow("Private memory", formatBytes(task.privateBytes))
                DetailRow(
                    "Disk",
                    "Read ${formatBytes(task.diskReadBytes)} (${formatBytes(task.diskReadBytesPerSec.toLong())}/s)\n" +
                        "Write ${formatBytes(task.diskWriteBytes)} (${formatBytes(task.diskWriteBytesPerSec.toLong())}/s)",
                )
                DetailRow(
                    "Network",
                    if (task.connectionCount == 0) {
                        "No IPv4 TCP/UDP sockets"
                    } else {
                        "${task.connectionCount} socket${if (task.connectionCount == 1) "" else "s"}" +
                            task.remoteEndpoints.takeIf { it.isNotEmpty() }?.joinToString(
                                prefix = "\n",
                                separator = "\n",
                                transform = { "→ $it" },
                            ).orEmpty()
                    },
                )
                task.path?.let { DetailRow("Location", it) }
                task.publisher?.let { DetailRow("Publisher", it) }
                task.version?.let { DetailRow("Version", it) }
                DetailRow("Session / threads", "${task.sessionId} / ${task.threadCount}")
                if (task.startedAtMs > 0) DetailRow("Started", formatDate(task.startedAtMs))
                task.mainWindowTitle?.let { DetailRow("Window", it) }
                if (task.elevated == true) DetailRow("Privilege", "Elevated (administrator)")
                if (task.protected) {
                    Surface(
                        color = colors.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            WarningIcon(colors.onErrorContainer)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                task.protectedReason ?: "FrostLock protects this process.",
                                fontSize = 12.sp,
                                color = colors.onErrorContainer,
                            )
                        }
                    }
                }
                Text(
                    "Metrics are sampled over the last 350 ms on the PC. " +
                        "Per-process network shows active IPv4 TCP/UDP sockets; FrostLock reports network throughput for the whole PC.",
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !task.protected, onClick = onEnd) {
                Icon(Icons.Default.Delete, null, Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (task.protected) "Protected" else "End task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            label.uppercase(Locale.getDefault()),
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
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
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a program on ${profile.name}") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it.take(4096) },
                    label = { Text("Full program path") },
                    placeholder = { Text("C:\\Windows\\System32\\notepad.exe") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = arguments,
                    onValueChange = { arguments = it.take(2048) },
                    label = { Text("Arguments (optional)") },
                    placeholder = { Text("Use quotes for spaces: \"C:\\My Files\\test.txt\"") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = directory,
                    onValueChange = { directory = it.take(4096) },
                    label = { Text("Working directory (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Use a full path that exists on the PC. Phones may start .exe, .bat, .cmd, and .com programs. " +
                        "The task runs in your signed-in Windows session.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = path.isNotBlank() && !busy,
                onClick = { onStart(path.trim(), splitArguments(arguments), directory.trim().ifBlank { null }) },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                else Text("Start")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun WarningIcon(tint: androidx.compose.ui.graphics.Color) {
    Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = tint)
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
