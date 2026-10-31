# FrostLock Remote

Android companion app for paired FrostLock PCs. The app is Kotlin + Jetpack Compose and is designed for private LAN or user-managed VPN use; it has no cloud account, relay, or background listener.

## Current state

This repository contains the first Android client implementation: pairing QR scan/manual entry, certificate-pinned HTTPS, an Android Keystore ECDSA device key, local paired-PC storage, status refresh, freeze, and confirmed unlock flows.

The matching Windows host is implemented in the FrostLock desktop source repository. Live control requires that build of FrostLock to be running with **Settings → Mobile Unlock** enabled. The API and signature format are a development contract and still need end-to-end tests on a phone and Windows PC before release. This client fails closed and never treats an offline PC as idle/unlocked.

The pairing QR JSON currently expected by the app is:

```json
{
  "v": 1,
  "endpoint": "https://192.168.1.20:44123",
  "certificateSha256": "<64 lowercase hex characters>",
  "token": "<short-lived one-use token>",
  "expiresAt": 1800000100,
  "pcName": "Office PC"
}
```

The Android app pins the SHA-256 digest of the exact server certificate from that invite. Pairing persists only after the server returns an approved device ID. The signing key is non-exportable and stored in Android Keystore. Removing a PC deletes the local key but does not revoke the desktop record; revoke lost devices on the PC.

The client sends `token`, `deviceName`, and the Base64-encoded X.509 public key to `/v1/pair`; FrostLock waits for approval on the PC before returning a device ID. Authenticated requests use the UTF-8 signing input `METHOD\nPATH\nNONCE\nTIMESTAMP\nREQUEST_ID\nLOWERCASE_SHA256_BODY_HEX`, signed with ECDSA/SHA-256, and the `X-FrostLock-*` headers implemented in both repositories. `TIMESTAMP` comes from the server's challenge response. Cross-platform test vectors and end-to-end tests remain to be added before a public release.

## Build

Requirements: JDK 17 or newer and Android SDK Platform 36. Build the debug APK with:

```powershell
\.\gradlew.bat assembleDebug
```

APK output: `app\build\outputs\apk\debug\app-debug.apk`.

On first build, Gradle downloads the wrapper and Maven dependencies. No signing keys or machine-specific SDK paths belong in Git.

## Security notes

- Cleartext traffic and redirects are disabled.
- HTTPS trusts only the certificate whose full SHA-256 digest was transferred during pairing.
- The PC remains authoritative for mode and remote-release policy. Remote starts are limited to timed Gentle/Strict freezes (5–60 minutes); Hard and USB-triggered freezes stay local-only. PIN release is denied unless explicitly enabled on the PC.
- Pairing requires desktop approval before the app stores a profile.
- No telemetry, cloud service, or automatic background polling is included.

The protocol is still a development contract, not a security-reviewed release protocol. Do not expose the Windows listener to public networks or use this APK for production control until cross-platform and end-to-end tests are available. Windows refuses to listen while a Public firewall profile is active; a Private-profile firewall rule may still be needed.
