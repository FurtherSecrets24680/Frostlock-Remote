package com.frostlock.remote.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frostlock.remote.MainActivity
import com.frostlock.remote.data.AppPreferences
import com.frostlock.remote.data.PcProfile
import com.frostlock.remote.data.PreferencesStore
import com.frostlock.remote.data.ProfileStore
import com.frostlock.remote.ui.FrostTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs when a FrostLock widget is added, so the widget can be bound to a paired PC.
 * The launcher discards the widget unless this returns RESULT_OK.
 */
class WidgetConfigureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setResult(RESULT_CANCELED)

        val profiles = ProfileStore(this)
        val preferences = PreferencesStore(this)
        setContent {
            val currentPreferences by preferences.flow.collectAsState(initial = AppPreferences())
            FrostTheme(currentPreferences.theme) {
                var available by remember { mutableStateOf<List<PcProfile>?>(null) }
                LaunchedEffect(Unit) {
                    available = withContext(Dispatchers.IO) {
                        profiles.all().sortedByDescending { it.lastSeenAt ?: it.pairedAt }
                    }
                }

                ConfigureScreen(
                    profiles = available,
                    onPick = { profile ->
                        WidgetStore(applicationContext).bind(widgetId, profile.id)
                        FrostLockWidget.refresh(applicationContext, intArrayOf(widgetId))
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                        )
                        finish()
                    },
                    onOpenApp = {
                        startActivity(Intent(this@WidgetConfigureActivity, MainActivity::class.java))
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}

@Composable
private fun ConfigureScreen(
    profiles: List<PcProfile>?,
    onPick: (PcProfile) -> Unit,
    onOpenApp: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(containerColor = colors.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "Widget setup",
                color = colors.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "Choose a PC",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
                modifier = Modifier.padding(top = 5.dp),
            )
            Text(
                "This widget freezes and unfreezes the PC you pick here.",
                color = colors.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )

            when {
                profiles == null -> Box(
                    Modifier.fillMaxWidth().padding(top = 30.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                profiles.isEmpty() -> {
                    Text(
                        "No PC is paired yet. Pair one in FrostLock Remote first.",
                        color = colors.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onOpenApp, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Text("Open FrostLock Remote")
                    }
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }

                else -> {
                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(profiles, key = { it.id }) { profile ->
                            Surface(
                                color = colors.surface,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, colors.outlineVariant),
                                modifier = Modifier.fillMaxWidth().clickable { onPick(profile) },
                            ) {
                                Row(
                                    Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Devices, null, tint = colors.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            profile.name,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurface,
                                        )
                                        Text(
                                            profile.endpoint.removePrefix("https://"),
                                            color = colors.onSurfaceVariant,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("Cancel") }
                }
            }
        }
    }
}
