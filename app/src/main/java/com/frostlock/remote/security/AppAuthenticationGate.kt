package com.frostlock.remote.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon

@Composable
fun AppAuthenticationGate(
    unavailable: Boolean,
    onRetry: () -> Unit,
    onOpenDeviceSettings: () -> Unit,
    onDisableAppLock: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (unavailable) Icons.Default.Security else Icons.Default.Fingerprint,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            if (unavailable) "Device authentication unavailable" else "FrostLock Remote is locked",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (unavailable) {
                "Set up a fingerprint or screen lock on this phone to continue. If you removed it, you can turn off app protection."
            } else {
                "Use your fingerprint or device screen lock to access your paired PCs."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(if (unavailable) "Try again" else "Unlock")
        }
        if (unavailable) {
            OutlinedButton(onClick = onOpenDeviceSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Open phone security settings")
            }
            OutlinedButton(onClick = onDisableAppLock, modifier = Modifier.fillMaxWidth()) {
                Text("Turn off app protection")
            }
        }
    }
}
