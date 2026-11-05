package com.frostlock.remote.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.frostlock.remote.data.ThemeMode
import com.frostlock.remote.ui.FrostTheme

class AppAuthenticationActivity : AppCompatActivity() {
    private var promptStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FrostTheme(ThemeMode.from(intent.getStringExtra(EXTRA_THEME))) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                    Text("Unlock FrostLock Remote", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Confirm with your fingerprint or device screen lock",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        if (promptStarted) return
        promptStarted = true
        authenticate()
    }

    private fun authenticate() {
        val biometric = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        val credential = getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        when (chooseAuthenticatorsFor(Build.VERSION.SDK_INT, biometric, credential)) {
            UnlockAuth.SKIP -> finishUnavailable()
            UnlockAuth.BIOMETRIC -> showPrompt(BiometricManager.Authenticators.BIOMETRIC_WEAK, true)
            UnlockAuth.DEVICE_CREDENTIAL -> showPrompt(BiometricManager.Authenticators.DEVICE_CREDENTIAL, false)
            UnlockAuth.BIOMETRIC_OR_CREDENTIAL -> showPrompt(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                false,
            )
        }
    }

    private fun showPrompt(authenticators: Int, allowCancelButton: Boolean) {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(com.frostlock.remote.R.string.app_auth_title))
            .setSubtitle("Protect access to your paired PCs")
            .setConfirmationRequired(false)
            .setAllowedAuthenticators(authenticators)
        if (allowCancelButton) builder.setNegativeButtonText("Cancel")

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    setResult(Activity.RESULT_OK)
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                        errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL
                    ) {
                        finishUnavailable()
                    } else {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                }
            },
        )

        runCatching { prompt.authenticate(builder.build()) }
            .onFailure { finishUnavailable() }
    }

    private fun finishUnavailable() {
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_AUTH_UNAVAILABLE, true))
        finish()
    }

    companion object {
        const val EXTRA_AUTH_UNAVAILABLE = "app_auth_unavailable"
        private const val EXTRA_THEME = "theme_mode"

        fun intent(context: Context, theme: ThemeMode) =
            Intent(context, AppAuthenticationActivity::class.java)
                .putExtra(EXTRA_THEME, theme.name)

        fun canAuthenticate(context: Context): Boolean {
            val biometric = BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
            val credential = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
            return biometric || credential
        }
    }
}
