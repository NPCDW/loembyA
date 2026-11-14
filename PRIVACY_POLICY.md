# Privacy Policy for loembyA

**Effective Date:** September 27, 2026  
**Application Name:** loembyA<br>
**Package Name:** `com.github.npcdw.loembya` (release), `com.github.npcdw.loembya.debug` (debug), `com.github.npcdw.loembya.preview` (preview)<br>
**Upstream Historical Privacy Policy (mpvEx):** [https://marlboro-advance.github.io/mpvEx/privacy-policy.html](https://marlboro-advance.github.io/mpvEx/privacy-policy.html) — This upstream historical policy does not represent the privacy policy of this loembyA fork.<br>
**License:** Apache License 2.0  

---

## 🔒 Summary (TL;DR)

- **No Developer Telemetry:** The application does not automatically send analytics or crash reports to the loembyA developers. Normal operation exchanges configuration, media metadata and playback reports with your configured PostgreSQL, Emby and enabled tracking services.
- **No Ads & No Analytics:** The application contains zero advertising SDKs, zero analytics tracking (no Google/Firebase Analytics), and zero telemetry.
- **Data Ownership:** Room stores local playback state, Emby identity mappings and search history. Shared server configuration and playback history belong to your configured PostgreSQL database; enabled Trakt, Simkl and YamTrack services receive playback metadata.
- **100% Open Source:** loembyA is free and open-source software. Its direct upstream is [CineIsle](https://github.com/nu1lkali/CineIsle), built on the earlier [mpvEx](https://github.com/marlboro-advance/mpvEx) project.

---

## 1. Introduction

loembyA ("the Application", "we", "us", or "our") is an open-source media player for Android built upon the `libmpv` playback engine. We are committed to maintaining the highest privacy standards. This Privacy Policy describes how loembyA handles information on Android devices.

The developers do not operate user databases or tracking services for the application. You configure access to your own shared PostgreSQL/Emby services and may enable third-party playback tracking.

---

## 2. Information We Do NOT Collect

We do not collect or monitor any personal or device data:

- **Personal Information:** No names, email addresses, phone numbers, or physical addresses.
- **Device Identifiers:** No IMEI, Android ID, MAC address, advertising ID, or hardware fingerprints.
- **Usage Telemetry:** No analytics libraries, event trackers, or usage heatmaps.
- **Media Content:** We never upload, inspect, or transfer your video files, audio files, photos, or documents to external servers.

---

## 3. Android Permissions & Usage

To function properly as a media player, loembyA requests the following Android permissions. Each permission is used strictly for on-device functionality:

| Permission | Category | Purpose |
| :--- | :--- | :--- |
| `READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE` | Storage | Legacy storage declarations do not enable local audio/video playback. Independent MediaInfo analysis remains available; subtitles, fonts and configuration use system document access. |
| `MANAGE_EXTERNAL_STORAGE` *(Standard flavor only)* | All Files Access | This legacy declaration does not enable file browsing or file management. Subtitle, font and configuration access uses the system document picker. *Not present in Google Play Scoped Storage builds.* |
| `INTERNET` | Network | Required to retrieve and play Emby media, including associated HTTP(S) streams, and check for GitHub release updates (standard build only). No arbitrary "Open URL" or independent SMB/WebDAV/FTP playback is provided. |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Background Playback | Allows uninterrupted audio and video playback when navigating outside the app, using Picture-in-Picture (PiP), or turning off the screen. |
| `POST_NOTIFICATIONS` | Notifications | Displays ongoing playback controls (play, pause, seek, track info) on Android 13+. |
| `REQUEST_INSTALL_PACKAGES` *(Standard flavor only)* | Updates | Allows self-updating the APK when a user initiates an update from GitHub releases. Not used for third-party APKs. |

You may grant or revoke permissions at any time via Android System Settings (**Settings > Apps > loembyA > Permissions**).

---

## 4. Local Data Storage & User Data Control

Local app data and shared service data are separate:

- **Local State:** Room stores playback state, server identity mappings and search history. PostgreSQL connection configuration is encrypted with Android Keystore AES-GCM in `noBackupFilesDir`.
- **Shared State:** Server configuration and shared playback history remain in your PostgreSQL database. Android writes only the typed automatic playback/history synchronization and compare-and-set history mark operations.
- **App Preferences:** UI, playback and subtitle preferences use local preference storage; runtime cache budgets and selected service reverse routes are read from shared configuration.
- **Data Deletion:** Clearing Android application data or uninstalling removes local state, not shared PostgreSQL records or third-party tracking history.

---

## 5. Network Streaming & Third-Party Services

When you use your configured services:
- Emby requests use the bound server and its selected reverse route. Public icons, application updates, Trakt, Simkl and YamTrack can each use the shared reverse-route selections supported by loemby.
- A selected reverse proxy receives the requests routed through it, including the authentication headers or webhook destination needed by that service. Use only trusted reverse proxies.
- loembyA does not operate intermediary proxy or analytics servers. Normal forward-proxy configuration and its credentials are not read by Android.
- Playback session reports go to Emby; allowlisted history updates go to your PostgreSQL database. Enabled tracking services receive media identifiers and playback progress.
- Requests use the configured TLS policy. `danger_accept_invalid_certs` disables certificate/hostname checks and should be enabled only with informed consent.

---

## 6. Crash Reporting

loembyA provides an on-device crash dialog (`CrashActivity`) for diagnosing unexpected crashes. Crash logs are **never** transmitted automatically. If you choose to submit a bug report to our GitHub Issues page, you do so manually and can review or sanitize the log contents before submitting.

---

## 7. Children's Privacy

loembyA does not collect personal information from any user, including children under the age of 13. It complies with COPPA and GDPR regulations.

---

## 8. Open Source & Transparency

loembyA is licensed under the **Apache License 2.0**. We gratefully acknowledge the projects it is derived from; these upstream repositories are not the loembyA repository:
- Direct upstream: [CineIsle](https://github.com/nu1lkali/CineIsle)
- Earlier player foundation: [mpvEx](https://github.com/marlboro-advance/mpvEx)

---

## 9. Contact Us

If you have any questions or feedback regarding this Privacy Policy, use the issue tracker of the loembyA repository from which you obtained this application. The upstream repositories linked above document the project's origins and are not contact channels for this fork.
