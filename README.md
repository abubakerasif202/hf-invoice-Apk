# HF Removals Invoices — Android App

Android app for **HF Removals Adelaide**, built with Kotlin, Jetpack Compose and Material 3.

| | |
|---|---|
| App name | HF Removals Invoices |
| Application ID | `com.hfremovalsadelaide.invoices` |
| Backend (source of truth) | `https://hf-invoice-generator.vercel.app` |
| minSdk / targetSdk | 24 (Android 7.0) / 36 |
| compileSdk | 37.1 |
| Toolchain | AGP 9.5.2, Kotlin 2.4.21, Gradle 9.9.2 (via wrapper), JDK 21 |

## How it works

The app hosts the live invoice website in a hardened WebView inside a branded native shell, with three tabs:

- **Workspace** — the live website (login, customers, invoices, payments, settings).
- **Invoices** and **New Invoice** — native screens that call the same authenticated backend endpoint (`/api/manage`) using the WebView's session. They show only what the server returns.

All customer, invoice, payment and GST data stays on the hosted backend. The app has no database of its own, no Firebase/Supabase, and no embedded credentials or API keys.

## Downloads, sessions and navigation

- **Expired session.** If the server answers a PDF/CSV request with 401/403, redirects to a page on the site (the login), or returns an HTML page instead of a document, the app shows **Session Expired — Sign In Again** and opens the login page. Redirects are never followed with the session cookie.
- **Progress and cancel.** Downloads show a progress ring with a percentage (indeterminate if the server sends no length) and a **Cancel** button that tears the connection down immediately. One download runs at a time; tapping another says so instead of doing nothing.
- **Clear errors.** Not found, server error, no connection, timeout, too large, invalid PDF, storage failure and blocked redirect each have their own message.
- **Completion.** A notification (private on the lock screen) offers Open and Share; the in-app sheet offers Open, Share and Print (A4 request via Android's print framework).
- **Rotation and appearance changes** (rotation, dark mode, language, font size) are handled without recreating the Activity, and the same layout tree is used for portrait (bottom bar) and landscape/tablet (side rail), so the WebView and its page are kept. After process death the last trusted page is restored (re-validated; API routes are never restored).

## Security notes

- **HTTPS only.** `network_security_config.xml` blocks cleartext traffic; TLS errors are never bypassed.
- **Strict trusted origin.** Only `https://hf-invoice-generator.vercel.app` loads in the WebView. The check rejects lookalike hosts, userinfo (`user@host`), backslash tricks, non-default ports and `..` in PDF paths (covered by unit tests). Other HTTPS links, `mailto:` and `tel:` open in the system handler; other schemes are blocked.
- **No JavaScript bridges**, no file/content access, mixed content blocked, Safe Browsing on, third-party cookies off.
- **Session cookies** are read in memory only, sent only to the trusted host over HTTPS (redirects are not followed), and never logged.
- **Downloaded documents.** PDFs are validated (`%PDF-` header, 25 MB cap), kept in app-private cache for Open/Share/Print via `FileProvider`, and also saved to `Downloads/HF Removals Invoices` using `MediaStore` (Android 10+) or app-scoped storage (Android 7–9).
- **Notifications** use private lock-screen visibility so invoice filenames are not shown on a locked phone.
- **Backup.** `allowBackup="false"` plus `data_extraction_rules.xml` exclude all app data from cloud backup and device transfer.
- **Privacy mode (optional, off by default).** *Workspace → gear icon → Settings → Privacy mode* sets `FLAG_SECURE`: screenshots and screen recording are blocked and the app preview is blanked in the recent-apps list. It is off by default so screenshots keep working; the choice is stored on the device only.

## Development setup (Windows 11 + PowerShell)

Prerequisites:

1. **JDK 21** (for example Eclipse Temurin 21).
2. **Android SDK** with platforms 36 and 37.1 and build-tools 36.0.0. Easiest: install Android Studio, then use SDK Manager. From the command line:
   ```powershell
   & "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-37.1" "platforms;android-36" "build-tools;36.0.0"
   ```
3. Environment variables: `ANDROID_HOME` pointing at the SDK (or a git-ignored `local.properties` containing `sdk.dir=...`), and `JAVA_HOME` pointing at JDK 21.

Clone and open:

```powershell
git clone https://github.com/abubakerasif202/hf-invoice-Apk.git
cd hf-invoice-Apk
```

Open the folder in Android Studio and let Gradle sync. Always use the wrapper (`gradlew.bat`), not a globally installed Gradle, so everyone builds with the same version.

## Build, test, run

```powershell
# Unit tests (JVM, Robolectric)
.\gradlew.bat :app:testDebugUnitTest

# Android Lint
.\gradlew.bat :app:lintDebug

# Debug APK  ->  app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat :app:assembleDebug

# Everything CI runs
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Debug builds are signed with the standard Android debug key that Gradle creates automatically in `%USERPROFILE%\.android\debug.keystore`. No keystore is needed in the repository.

> Do not run two Gradle builds at the same time in the same checkout. On Windows this can corrupt intermediate dex files and produce confusing `dexBuilderDebug` errors.

### Install on a phone or emulator

1. On the phone: enable **Developer options** (tap *Build number* 7 times) and **USB debugging**; approve the prompt when connected.
2. Check the device is listed, then install:
   ```powershell
   adb devices
   adb -s <serial> install -r app\build\outputs\apk\debug\app-debug.apk
   ```
3. To use an emulator instead, create a device in Android Studio's Device Manager (API 24–36) and start it; `adb devices` will then list it.

Or copy the APK to the phone and open it, allowing *Install unknown apps* for your file manager when asked.

## Signed releases (APK and AAB)

Never commit a keystore or its passwords. Create a private keystore once and keep a backup outside the repository:

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore "$env:USERPROFILE\hf-removals-release.jks" -keyalg RSA -keysize 2048 -validity 10000 -alias hf-removals-release
```

Provide the signing details either through a git-ignored `keystore.properties` in the project root:

```properties
storeFile=C:/Users/you/hf-removals-release.jks
storePassword=...
keyAlias=hf-removals-release
keyPassword=...
```

or through environment variables for the current session:

```powershell
$env:HF_RELEASE_STORE_FILE     = "$env:USERPROFILE\hf-removals-release.jks"
$env:HF_RELEASE_STORE_PASSWORD = "..."
$env:HF_RELEASE_KEY_ALIAS      = "hf-removals-release"
$env:HF_RELEASE_KEY_PASSWORD   = "..."
```

Then build:

```powershell
.\gradlew.bat :app:assembleRelease :app:bundleRelease
```

Outputs:

- Signed APK (direct install): `app\build\outputs\apk\release\app-release.apk`
- Signed AAB (Google Play): `app\build\outputs\bundle\release\app-release.aab`

Without signing details, `assembleRelease` produces `app-release-unsigned.apk`, which **cannot be installed** and is not distribution-ready. Verify a signed APK with:

```powershell
& "$env:ANDROID_HOME\build-tools\36.0.0\apksigner.bat" verify --verbose app\build\outputs\apk\release\app-release.apk
```

Release builds use R8 shrinking and resource shrinking. Keep your keystore: Google Play and in-place updates require the same key every time.

### Updating the version

Edit `versionCode` (must increase with every release) and `versionName` in `app/build.gradle.kts`, then rebuild.

## GitHub Actions

`.github/workflows/android-apk.yml` (actions: `checkout@v7`, `setup-java@v6`, `setup-gradle@v6`, `upload-artifact@v7`, `setup-android@v4`) runs on pushes to `main`, pull requests and manual dispatch. It runs unit tests, lint and `assembleDebug`, verifies the APK is a valid archive, and uploads it as the artifact **HF-Removals-Invoices-Debug-APK** (kept 14 days). On failure it uploads test and lint reports as **build-diagnostics**.

To download the APK: GitHub → **Actions** → the run → **Artifacts**.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Minimum supported Gradle version is ...` | You ran a different Gradle than the wrapper. Use `.\gradlew.bat`. |
| `requires ... compile against version 37.1 or later` | Install `platforms;android-37.1` with SDK Manager. |
| `SDK location not found` | Set `ANDROID_HOME` or create `local.properties` with `sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk`. |
| `Unsupported class file major version` | Wrong JDK. Set `JAVA_HOME` to JDK 21. |
| `Failed to create MD5 hash ... dexBuilderDebug` | Two builds ran at once, or a stray Java process is holding files. Stop Java processes and rebuild. |
| `adb: no devices` | Re-plug USB, accept the debugging prompt, check `adb devices`. |
| Release APK will not install | It is unsigned. Provide signing details (see above). |
| PDF does not open | Install a PDF viewer app; the app falls back to a clear error message. Share and Print still work. |
| "Sign in again" when downloading | The server session expired. Sign in in the Workspace tab and retry. |
| PDF/CSV saved but not visible | Android 10+: look in `Downloads/HF Removals Invoices`. Android 7–9: app-specific storage (`Android/data/com.hfremovalsadelaide.invoices/files/Download`). |

## Tests

`.\gradlew.bat :app:testDebugUnitTest` runs 69 JVM tests, including a contract test against real backend output, (Robolectric, no emulator needed): trusted-origin policy (lookalike hosts, userinfo, backslashes, traversal, restore-URL validation), the downloader against a scripted fake connection (session expiry, redirects, error mapping, progress, size cap, cookie handling, cancellation), ViewModel download state and events, Privacy mode, the adaptive layout (WebView host survives rotation), manifest configuration, and the invoice API parser.

## Jobs tab (Jobs & Dispatch)

A fourth tab shows bookings synchronised by the backend from the HF Google Calendar. It needs the matching backend release (`/api/jobs`); until that is deployed the tab reports a request error.

- **Calendar views:** Agenda, Day, Week and Month, with Today/Tomorrow shortcuts and a dashboard (today, upcoming, in progress, completed, sign-off due, unconfirmed, possible crew/truck conflicts).
- **Job details:** customer, pickup and drop-off, package, crew, notes, with Call and Navigate buttons. Missing values show "Not provided"; nothing is guessed. Google's `confirmed` status is never treated as a confirmed booking.
- **Scheduled vs actual:** scheduled times come from the calendar and are never edited. Start, Pause/Resume and Finish record the server clock. Corrections need a reason and the original is kept in the audit trail.
- **Sign-off (optional):** after Finish, the customer can sign with a finger and acknowledge the summary (the text states it does not waive rights or approve extra charges), or the driver can Skip with a reason. A skipped job stays "completed, not signed". A completion PDF is available for completed jobs.
- **Create Invoice From Job:** shows a review of a prefilled draft (working time, break, package rate, warnings) and saves a **draft** only. It never issues an invoice.
- **Notifications:** per-category toggles (24 h, 1 h, 15 min, start, running late, completed, sign-off pending, rescheduled/cancelled, assignment change). Scheduled with WorkManager, so delivery is not exact-to-the-minute and may be delayed by battery saver. Reminders are de-duplicated by a stable key, survive restarts and reboots, re-check the job state before firing, and never include phone numbers or addresses (private on the lock screen). Tapping one opens the job. No server push is used yet.
- **Not yet available:** separate driver/subcontractor logins (the app still uses the single administrator session).

Core library desugaring is enabled so `java.time` works on Android 7.
## Known limitations

- **No instrumented (on-device UI) test suite and no emulator run** is part of CI. Device checks so far were manual, on one Huawei phone (Android 10).
- **Printing** uses Android's print framework and requests A4; the paper size actually offered depends on the selected printer/print service (one test printer defaulted to Letter).
- **The website itself is not changed by this app.** Its own header and layout (including in landscape) are what the hosted site renders; the native shell only adds the app bar and navigation.
- **Download progress is shown in the app, not in the notification shade**, and a download stops if Android kills the app process.
- **Session expiry detection** relies on the server answering with 401/403, a redirect to the site, or an HTML page; a server that signals expiry some other way would show a generic error.
- **Signed releases need your own keystore**, which is deliberately not in this repository.

## Project layout

```
app/src/main/java/com/hfremovalsadelaide/invoices/
  security/   TrustedOriginPolicy (URL allowlist, filename sanitising)
  webview/    hardened WebView factory and callbacks
  pdf/        authenticated downloads, Open/Share/Print, notifications
  data/       /api/manage client and models
  network/    connectivity monitor
  ui/         Compose shell, Workspace, Invoices, New Invoice, theme
app/src/test/ unit tests (policy, repository, configuration)
```
