# FrostLock Remote for Android

FrostLock Remote lets you check and control a paired Windows PC from your Android phone. Pair once on your private network, then use the PC page to start or release a freeze. The PC keeps control of the lock policy.

[FrostLock for Windows](https://github.com/FurtherSecrets24680/FrostLock) · [Android CI builds](https://github.com/FurtherSecrets24680/Frostlock-Remote/actions/workflows/android.yml)

## What the app does

- Shows paired PCs and whether each one is reachable, idle, or frozen.
- Opens a dedicated control page for each PC. The large Freeze/Unlock control acts on that PC.
- Uses the mode and duration saved in that PC’s **Lock options**. Change those options from the PC page; the app loads them from Windows before enabling a new freeze.
- Applies the PC’s safety limit and release policy. An offline PC has an unknown state, not an unlocked state.
- Pairs by scanning a QR code or entering its invite text. Camera access is optional; manual entry does not need it.

The phone can start Gentle, Strict, or configured PIN freezes. Hard and USB Key modes remain PC-only. A phone can release a freeze only when the PC permits remote release. Remote release for PIN mode stays off unless the PC owner enables it.

## Pair your phone

1. Install FrostLock on Windows and connect the PC and phone to the same private Wi-Fi network, or a VPN that can reach the PC.
2. In FrostLock for Windows, open **Settings → Mobile Unlock**, enable mobile control, and create a pairing code.
3. In FrostLock Remote, tap **Pair a PC**. Scan the code or choose manual entry and paste it.
4. Approve the pairing request on the Windows PC. The phone saves the pairing only after the PC approves it.

If the PC does not appear, check that FrostLock is running, both devices can reach the shown PC address, and Windows Firewall allows FrostLock on the **Private** network profile. The Windows listener does not start on a Public firewall profile. Do not expose its port to the public internet or configure router port forwarding.

## Use the controls

Tap a PC card to open its control page. The app checks the PC’s status and saved lock options before enabling Freeze. The button shows the configured mode and duration. Tap it once to start the freeze; when the PC reports an active freeze, tap Unlock if its policy allows release.

Use **Lock options** on that page to change the mode, duration, arm delay, safety auto-release, lock screen, and related behavior. Save to send the settings to Windows. The next freeze uses the saved mode and duration. The PC may also apply a safety auto-release limit to an indefinite freeze.

Tap **Refresh** to check status again. If a PC is offline, the app disables actions that depend on knowing its state.

## Requirements

- Android 8.0 (API 26) or newer.
- FrostLock for Windows with Mobile Unlock enabled.
- A private Wi-Fi or VPN connection between the phone and PC. The app does not use a cloud account or relay.

The app checks status when it opens and when you refresh. It does not run a background listener or continuously poll PCs.

## Security and stored data

- The app connects directly to the Windows PC over HTTPS. It pins the SHA-256 fingerprint of the certificate delivered during pairing and rejects redirects and cleartext traffic.
- Each pairing gets an ECDSA signing key in Android Keystore. The private key is non-exportable; the app signs authenticated requests with it.
- The app stores paired PC details locally and disables Android backup for app data. Removing a PC deletes its local key and pairing record. To remove a lost or retired phone completely, also revoke it in FrostLock on Windows.
- The Windows PC approves each pairing and decides which lock modes and remote releases it permits.
- The app has no telemetry, cloud service, relay, or background polling.

## Build and test

### Local development

Use JDK 17 and Android SDK Platform 36. From the repository root on Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

### GitHub Actions

Open **Actions → Android CI → Run workflow**. Leave **Build the release APK and app bundle** unchecked for a debug APK, or select it for release artifacts. The workflow runs the matching unit tests before uploading the build artifacts.

The release workflow currently has no release signing key configured. It uploads an unsigned APK (`app-release-unsigned.apk`) and an Android App Bundle (`app-release.aab`). Sign the APK with your own key before distributing it. Do not publish a debug-key-signed APK as a production release.

To build the current working tree, including uncommitted changes, use:

```powershell
.\tools\ci-snapshot.ps1 -Release
```

This helper pushes a temporary snapshot branch to GitHub so Actions can build it, downloads artifacts into `build\release`, then removes the branch. Anyone with repository access can see the snapshot while the build runs.

## Project details

The Android client uses Kotlin, Jetpack Compose, DataStore, OkHttp, CameraX, and ML Kit barcode scanning. Pairing and request signing must remain compatible with the Windows implementation in the [FrostLock repository](https://github.com/FurtherSecrets24680/FrostLock).
