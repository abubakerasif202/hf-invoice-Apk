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

The native app brings together the dashboard, jobs and dispatch, customers, invoicing, fleet and staff tools. Staff sign in with their own accounts. The backend determines which features and bookings each account can access.

- **Workspace** opens the live website in a hardened WebView using the same session.
- **Invoices** and **New Invoice** use the authenticated `/api/manage` endpoint to review and manage invoices.

The backend is the source of truth for customer, invoice, payment and GST records. An account-scoped, encrypted cache supports offline job access and approved field updates. The app clears private cached data and pending document shares when the active account changes.

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
.\gradlew.bat :app:testQaUnitTest

# Android Lint
.\gradlew.bat :app:lintDebug

# Debug APK  ->  app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat :app:assembleDebug

# Everything CI runs
.\gradlew.bat :app:testQaUnitTest :app:lintDebug :app:assembleDebug :app:assembleQa :app:assembleQaAndroidTest
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

Use the existing signing key for updates. Keep its backup and credentials outside the repository.

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
& "$env:ANDROID_HOME\build-tools\36.0.0\apksigner.bat" verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
```

Release builds use R8 shrinking and resource shrinking. Keep your keystore: Google Play and in-place updates require the same key every time.

### Updating the version

Edit `versionCode` (must increase with every release) and `versionName` in `app/build.gradle.kts`, then rebuild.

## GitHub Actions

`.github/workflows/android-apk.yml` runs on pushes to `main`, pull requests and manual dispatch. It runs QA unit tests, debug lint, and builds the debug, QA and QA instrumentation APKs. When signing secrets are available, it also builds and verifies the signed release APK against the existing certificate before uploading **HF-Removals-Invoices-Release-APK** for 14 days. On failure it uploads test and lint reports as **build-diagnostics**.

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

`.\gradlew.bat :app:testQaUnitTest` runs the JVM suite with Robolectric. It covers trusted-origin policy, authenticated document downloads, role-based navigation, offline operations, jobs, invoice parsing and the adaptive layout. Gradle writes the results to `app/build/reports/tests/testQaUnitTest/index.html`.

## Jobs tab (Jobs & Dispatch)

A fourth tab shows bookings synchronised by the backend from the HF Google Calendar. It needs the matching backend release (`/api/jobs`); until that is deployed the tab reports a request error.

- **Calendar views:** Agenda, Day, Week and Month, with Today/Tomorrow shortcuts and a dashboard (today, upcoming, in progress, completed, sign-off due, unconfirmed, possible crew/truck conflicts).
- **Job details:** customer, pickup and drop-off, package, crew, notes, with Call and Navigate buttons. Missing values show "Not provided"; nothing is guessed. Google's `confirmed` status is never treated as a confirmed booking.
- **Scheduled vs actual:** scheduled times come from the calendar and are never edited. Start, Pause/Resume and Finish record the server clock. Corrections need a reason and the original is kept in the audit trail.
- **Sign-off (optional):** after Finish, the customer can sign with a finger and acknowledge the summary (the text states it does not waive rights or approve extra charges), or the driver can Skip with a reason. A skipped job stays "completed, not signed". A completion PDF is available for completed jobs.
- **Create Invoice From Job:** shows a review of a prefilled draft (working time, break, package rate, warnings) and saves a **draft** only. It never issues an invoice.
- **Notifications:** per-category toggles (24 h, 1 h, 15 min, start, running late, completed, sign-off pending, rescheduled/cancelled, assignment change). Scheduled with WorkManager, so delivery is not exact-to-the-minute and may be delayed by battery saver. Reminders are de-duplicated by a stable key, survive restarts and reboots, re-check the job state before firing, and never include phone numbers or addresses (private on the lock screen). Tapping one opens the job. No server push is used yet.
- **Staff access:** invited staff sign in with their own account. Drivers, movers and subcontractors see their assigned bookings. The backend enforces access to dispatch, customer records, invoicing and reporting.

Core library desugaring is enabled so `java.time` works on Android 7.
## One signing key for every build (updates install over the app)

Local and GitHub Actions builds are signed with the **same** key, so a new APK always installs over the existing app without uninstalling or losing settings.

- The key is the app's long-term key. Its private copy lives outside Git: `%USERPROFILE%\.android\debug.keystore` on the developer machine (back it up) and four GitHub Actions secrets: `HF_SIGNING_KEYSTORE_B64`, `HF_SIGNING_STORE_PASSWORD`, `HF_SIGNING_KEY_ALIAS`, `HF_SIGNING_KEY_PASSWORD`.
- CI decodes it into the runner's temp folder at build time, builds, and prints the certificate SHA-256 in the run summary. Current fingerprint: `cbf41361728b002f46a4b5b88c368411b86055af9c36acdd4b82f65e0e002d91`.
- Builds without the secrets (fork pull requests) fall back to Android's default debug key; those APKs will **not** install over this app.
- Check any APK: `apksigner verify --print-certs app-debug.apk`.
- Release builds use the same mechanism (`keystore.properties` or `HF_RELEASE_*`), so a signed release also matches.
- **Upgrading to a dedicated, stronger key later** changes the signature, so Android requires uninstalling once (settings are lost). Do it deliberately, with approval, then replace the four secrets.

## On-device end-to-end tests (read this first)

`app/src/androidTest` holds end-to-end flows (calendar, Start/Pause/Resume/Finish, signature draw/clear/save, skip, PDF request, invoice draft review) that run against an in-memory fixture, plus a notification-privacy test. The flows live in `app/src/sharedTest` (`JobsFlowBase`, `FakeJobsGateway`).

**Do not run them on a phone that holds real app data.** During testing on a Huawei VOG-L29 (Android 10) the system uninstall dialog appeared during the run and the installed app was removed, which also deletes its sign-in and settings. The cause was not identified. The Compose flows also failed to start an activity on that handset ("No compose hierarchies found"), while the notification test passed. Also never use `connectedAndroidTest` (it uninstalls the app afterwards).

Run the QA APK and its separate test package on an isolated Android emulator. Use the serial shown by `adb devices -l`.

```powershell
.\gradlew.bat :app:assembleQa :app:assembleQaAndroidTest
adb -s emulator-5562 install -r app\build\outputs\apk\qa\app-qa.apk
adb -s emulator-5562 install -r app\build\outputs\apk\androidTest\qa\app-qa-androidTest.apk
adb -s emulator-5562 shell pm grant com.hfremovalsadelaide.invoices.qa android.permission.POST_NOTIFICATIONS
adb -s emulator-5562 shell am instrument -w com.hfremovalsadelaide.invoices.qa.test/androidx.test.runner.AndroidJUnitRunner
```

The same logic is covered on the JVM by the ViewModel, contract, renderer and policy tests that run in every build.
## Privacy of signed documents

Job completion PDFs can contain a customer's signature. They are **never** copied into shared storage automatically: they stay in the app's private cache (pruned automatically), the sheet says "Kept private in the app", and exporting is an explicit Share/Open/Print action. Invoice PDFs keep their existing behaviour.

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
