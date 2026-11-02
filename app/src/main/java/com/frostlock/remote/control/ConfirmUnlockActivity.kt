package com.frostlock.remote.control

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.frostlock.remote.data.PcProfile
import com.frostlock.remote.data.ProfileStore
import com.frostlock.remote.security.UnlockAuth
import com.frostlock.remote.security.chooseAuthenticatorsFor
import com.frostlock.remote.widget.FrostLockWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The single confirmation screen every remote unlock goes through, so the app, the
 * widget, the Quick Settings tile and the freeze notification all ask the same question.
 *
 * It is translucent on purpose: the system biometric prompt is the visible part, and
 * this activity only exists to be a [FragmentActivity] that can host it. The unlock
 * itself runs here, and the result is reported back so the caller can refresh.
 */
class ConfirmUnlockActivity : FragmentActivity() {

    private var profile: PcProfile? = null
    private var started = false
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        setContent { UnlockBackdrop() }
        loadProfile()
    }

    override fun onPostResume() {
        super.onPostResume()
        // The prompt requires a resumed activity; a fast profile read can beat this.
        maybePrompt()
    }

    private fun loadProfile() {
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        if (profileId.isNullOrBlank()) {
            finishWith(false)
            return
        }
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                ProfileStore(this@ConfirmUnlockActivity).all().firstOrNull { it.id == profileId }
            }
            if (loaded == null) {
                RemoteControl.toast(this@ConfirmUnlockActivity, "That PC is no longer paired.")
                finishWith(false)
                return@launch
            }
            profile = loaded
            maybePrompt()
        }
    }

    private fun maybePrompt() {
        if (started || finished) return
        val pc = profile ?: return
        started = true

        // Confirm there is still a freeze to release before asking the phone to prove
        // who is holding it. A stale tap from the tile or widget ends here instead of
        // unlocking something that is already open.
        lifecycleScope.launch {
            val result = runCatching { RemoteControl.status(pc) }
            val snapshot = result.getOrNull()
            if (finished) return@launch
            if (snapshot == null) {
                RemoteControl.toast(
                    this@ConfirmUnlockActivity,
                    RemoteControl.shortError(result.exceptionOrNull() ?: IllegalStateException()),
                )
                finishWith(false)
                return@launch
            }
            if (!snapshot.isActive) {
                RemoteControl.reflect(this@ConfirmUnlockActivity, pc, snapshot)
                RemoteControl.toast(this@ConfirmUnlockActivity, "${pc.name} is not frozen.")
                finishWith(false)
                return@launch
            }
            // The user may have left while the check ran; never prompt from the background.
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                finishWith(false)
                return@launch
            }
            authenticate(pc)
        }
    }

    private fun authenticate(pc: PcProfile) {
        val biometric = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        val credential = getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

        when (chooseAuthenticatorsFor(Build.VERSION.SDK_INT, biometric, credential)) {
            UnlockAuth.SKIP -> performUnlock(pc)

            UnlockAuth.BIOMETRIC -> showPrompt(
                pc,
                BiometricManager.Authenticators.BIOMETRIC_WEAK,
                cancelButton = true,
            )

            UnlockAuth.DEVICE_CREDENTIAL -> showPrompt(
                pc,
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                cancelButton = false,
            )

            UnlockAuth.BIOMETRIC_OR_CREDENTIAL -> showPrompt(
                pc,
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                cancelButton = false,
            )
        }
    }

    private fun showPrompt(pc: PcProfile, authenticators: Int, cancelButton: Boolean) {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(com.frostlock.remote.R.string.confirm_unlock_title))
            .setSubtitle("Confirm it is you to release the freeze on ${pc.name}.")
            .setConfirmationRequired(false)
            .setAllowedAuthenticators(authenticators)
        // A negative button is not allowed when the device credential is offered.
        if (cancelButton) builder.setNegativeButtonText("Cancel")

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    profile?.let(::performUnlock)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val quiet = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    if (!quiet) RemoteControl.toast(this@ConfirmUnlockActivity, errString.toString())
                    finishWith(false)
                }

                override fun onAuthenticationFailed() {
                    // A single unreadable finger; keep the prompt up so the user can retry.
                }
            },
        )

        runCatching { prompt.authenticate(builder.build()) }
            .onFailure { finishWith(false) }
    }

    private fun performUnlock(pc: PcProfile) {
        lifecycleScope.launch {
            runCatching { RemoteControl.unlock(pc) }
                .onSuccess { snapshot ->
                    RemoteControl.reflect(this@ConfirmUnlockActivity, pc, snapshot)
                    FrostLockWidget.refreshAll(this@ConfirmUnlockActivity)
                    RemoteControl.toast(
                        this@ConfirmUnlockActivity,
                        if (snapshot.isActive) "The freeze is still active on ${pc.name}."
                        else "Unlocked ${pc.name}.",
                    )
                    finishWith(!snapshot.isActive)
                }
                .onFailure { error ->
                    RemoteControl.toast(this@ConfirmUnlockActivity, RemoteControl.shortError(error))
                    finishWith(false)
                }
        }
    }

    private fun finishWith(unlocked: Boolean) {
        if (finished) return
        finished = true
        setResult(
            if (unlocked) RESULT_OK else RESULT_CANCELED,
            Intent().putExtra(EXTRA_UNLOCKED, unlocked),
        )
        finish()
    }

    companion object {
        const val EXTRA_PROFILE_ID = "com.frostlock.remote.control.PROFILE_ID"
        const val EXTRA_UNLOCKED = "com.frostlock.remote.control.UNLOCKED"

        fun intent(context: Context, profileId: String): Intent =
            Intent(context, ConfirmUnlockActivity::class.java).putExtra(EXTRA_PROFILE_ID, profileId)
    }
}

@Composable
private fun UnlockBackdrop() {
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color(0xFF101E2E),
            contentColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(28.dp),
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "FROSTLOCK",
                    color = Color(0xFF7FD0EF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Confirm unlock",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Waiting for your phone to confirm it is you.",
                    color = Color(0xFFB6C6D2),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
