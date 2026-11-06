# FrostLock Remote for Android

FrostLock Remote lets you check and control a paired Windows PC from your Android phone. Pair once on your private network, then use the PC page to start or release a freeze, manage tasks, and inspect the connection. The PC keeps control of the lock policy.

[FrostLock for Windows](https://github.com/FurtherSecrets24680/FrostLock) · [Android CI builds](https://github.com/FurtherSecrets24680/Frostlock-Remote/actions/workflows/android.yml)

## What the app does

- Shows paired PCs and whether each one is reachable, idle, or frozen.
- Opens a dedicated control page for each PC. The large Freeze/Unfreeze control acts on that PC.
- Offers a home-screen widget with the same Freeze/Unfreeze circle for one chosen PC. Resize it from 2×2 upward; smaller placements switch to a compact layout.
- Adds a Quick Settings tile that freezes or unfreezes the PC you used last.
- Keeps an ongoing notification while a freeze is active, with an inline **Unfreeze** action.
- Asks for a fingerprint or the device PIN before sending each unfreeze.
- Shows connection details for each PC, including its endpoint, connection state, and pinned certificate fingerprint.
- Includes a task manager for each PC: live CPU, memory, disk, and network charts, a searchable process list, process details, end/force-end, restart, start-task, and a run-command box.
- Uses the mode and duration saved in that PC’s **Lock options**. Change those options from the PC page; the app loads them from Windows before enabling a new freeze.
- Applies the PC’s safety limit and release policy. An offline PC has an unknown state. Do not treat it as unfrozen.
- Pairs by scanning a QR code or entering the invite text by hand; camera access is optional.

The phone can start Gentle, Strict, or configured PIN freezes. Hard and USB key modes remain PC-only. A phone can release a freeze only when the PC permits remote release. Remote release for PIN mode stays off unless the PC owner enables it.

## Pair your phone

1. Install FrostLock on Windows and connect the PC and phone to the same private Wi-Fi network, or a VPN that can reach the PC.
2. In FrostLock for Windows, open **Settings → LAN & phone**, enable LAN control, and create a pairing code.
3. In FrostLock Remote, tap **Pair a PC**. Scan the code or choose manual entry and paste it.
4. Approve the pairing request on the Windows PC. The phone saves the pairing only after the PC approves it.

If the PC does not appear, check that FrostLock is running, both devices can reach the shown PC address, and Windows Firewall allows FrostLock on the **Private** network profile. The Windows listener does not start on a Public firewall profile. Do not expose its port to the public internet or configure router port forwarding.

## Use the controls

Tap a PC card to open its control page. The app checks the PC’s status and saved lock options before enabling Freeze. The button shows the configured mode and duration. Tap it once to start the freeze; when the PC reports an active freeze, tap Unfreeze if its policy allows release.

Use **Lock options** on that page to change the mode, duration, arm delay, safety auto-release, lock screen, and related behavior. Save to send the settings to Windows. The next freeze uses the saved mode and duration. The PC may also apply a safety auto-release limit to an indefinite freeze.

## Task manager

Open a PC and choose **Task manager** to see what is running on Windows. The overview shows live charts for CPU, memory, disk, and network, plus the process count. Each row shows the program icon, CPU, working memory, disk activity, and TCP/UDP socket count. Search, filter apps or background processes, group the list, and sort by CPU, memory, disk, network, or name. Tap a process for its full executable path, publisher, version, session, start time, and remote endpoints.

**End task** asks Windows to close the program first; **Force end** terminates it immediately. **Restart** closes and relaunches a program, and **Start task** runs an `.exe`, `.bat`, `.cmd`, or `.com` by full path with optional arguments. The **Run a command** action sends a command through `cmd.exe /c`; its output stays on the PC and is not shown on the phone. FrostLock, Windows core processes, and tasks the app cannot access are protected or reported with a reason. The PC owner must enable **Allow phone task control** in FrostLock's **Settings → LAN & phone**. This needs FrostLock for Windows 1.6.0 or newer. The phone only sends signed commands to the PC.

Tap **Refresh** to check status again. If a PC is offline, the app disables actions that depend on knowing its state.

## Home screen widget

Long-press an empty spot on the home screen, choose **Widgets**, and add **FrostLock PC control**. The launcher asks which paired PC the widget should control; one widget controls one PC, and adding a second widget lets you pick a different one.

The widget starts at 2×2 and can be resized horizontally and vertically. Placements under 150dp in either direction use the compact layout: a smaller circle and a one-line status, so nothing clips. Larger sizes show the full circle and mode/time line. The card wraps its content, so a tall cell leaves the extra space transparent.

The circle mirrors the app: a lock icon to freeze, an open lock and a red circle while a freeze is active, and a spinner while a request is in flight. The status line shows the mode and the time left as of the last check. Tap the circle to act; if the PC was unreachable, the tap retries the connection.

The widget redraws whenever the app checks a PC and when it is resized, and Android also refreshes it about every 30 minutes and after a reboot. It does not tick a live countdown between refreshes.

## Quick Settings tile

Open the notification shade, expand the tiles, and add **FrostLock PC**. The tile acts on the PC you opened most recently in the app, or the newest paired PC if you have not opened one yet.

Tapping the tile checks the PC's current state, then freezes it or unfreezes it. It makes the same decision as the app and widget, so a tile tap never guesses. While the check runs, the tile shows as unavailable.

## Freeze notification

While a freeze is active, FrostLock keeps one ongoing notification with the mode, a live countdown drawn by the system, and an **Unfreeze** action. The action opens the same confirmation screen as everything else, so it cannot bypass the phone lock. FrostLock posts or clears the notification the next time the app, widget, or tile talks to the PC, so a freeze that expires on its own may linger until the next check. Android 13 and newer ask for notification permission on first launch.

## Confirm before unfreezing

Starting a freeze never needs confirmation. Unfreezing does, because an unattended phone that is already unlocked should not be able to undo the PC's protection.

Every unfreeze opens the same confirmation screen, whether it starts in the app, the widget, the tile, or the notification. It asks for fingerprint or face with the device PIN, pattern, or password as a fallback. If the phone has a screen lock but no biometric, it asks for the device credential. If the phone has no screen lock at all, there is nothing to prove, so the app sends the unfreeze directly.

The app does not ask for authentication when you open it. It requests a scan only when you unfreeze. The confirmation also runs in its own task, so tapping a widget or tile does not pull the full app forward behind the prompt.

FrostLock never stores or sees your biometrics or PIN; Android's system prompt checks them and only tells the app "confirmed" or "not confirmed".

## Requirements

- Android 8.0 (API 26) or newer.
- FrostLock for Windows with LAN control enabled under **Settings → LAN & phone**. Version 1.6.0 or newer is needed for the task manager; freeze control works with 1.5.0 and older.
- A private Wi-Fi or VPN connection between the phone and PC. The app does not use a cloud account or relay.

The app checks status when it opens, when you refresh, when the widget is tapped, and when the tile is opened or tapped. It does not run a background listener on the phone. The widget refreshes only through the system's periodic update, about every 30 minutes.

## Security and stored data

- The app connects directly to the Windows PC over HTTPS. It pins the SHA-256 fingerprint of the certificate delivered during pairing and rejects redirects and cleartext traffic. The **Connection details** screen shows the pinned fingerprint so you can compare it with the PC.
- Each pairing gets an ECDSA signing key in Android Keystore. The private key is non-exportable; the app signs authenticated requests with it.
- The app stores paired PC details locally and disables Android backup for app data. Removing a PC deletes its local key and pairing record. To remove a lost or retired phone completely, also revoke it in FrostLock on Windows.
- The Windows PC approves each pairing and decides which lock modes, remote releases, and task actions it permits. Phone task control is off by default, and you can turn it off at any time.
- The app has no telemetry, cloud service, or relay. Every request goes directly to a paired PC.

## Build and test

### Local development

Use JDK 17 and Android SDK Platform 36. From the repository root on Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

### GitHub Actions

Open **Actions → Android CI → Run workflow**. Leave **Build the release APK and app bundle** unchecked for a debug APK, or select it for release artifacts. The workflow runs the matching unit tests before uploading the build artifacts.

The release workflow signs the APK and Android App Bundle with the FrostLock Remote release key stored in GitHub Actions secrets. The workflow decodes the keystore into the runner’s temporary directory; the keystore and passwords do not belong in Git. Keep a secure backup of the release keystore. Android accepts future updates only when they use the same signing key.

The release signing inputs are `FROSTLOCK_RELEASE_KEYSTORE_BASE64`, `FROSTLOCK_RELEASE_STORE_PASSWORD`, `FROSTLOCK_RELEASE_KEY_ALIAS`, and `FROSTLOCK_RELEASE_KEY_PASSWORD`. The workflow stops if any input is missing.

To build the current working tree, including uncommitted changes, use:

```powershell
.\tools\ci-snapshot.ps1 -Release
```

This helper pushes a temporary snapshot branch to GitHub so Actions can build it, downloads artifacts into `build\release`, then removes the branch. Anyone with repository access can see the snapshot while the build runs.

## Project details

The Android client uses Kotlin, Jetpack Compose, DataStore, OkHttp, CameraX, ML Kit barcode scanning, and AndroidX Biometric. Pairing and request signing must remain compatible with the Windows implementation in the [FrostLock repository](https://github.com/FurtherSecrets24680/FrostLock).
