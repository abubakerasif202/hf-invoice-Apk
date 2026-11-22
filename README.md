# HF Removals Invoices — Android Application

Native Android application for **HF Removals Adelaide** built with Kotlin, Jetpack Compose, Material 3, and Android Studio-compatible Gradle (Kotlin DSL).

- **App Name:** HF Removals Invoices
- **Package / Application ID:** `com.hfremovalsadelaide.invoices`
- **Backend Source of Truth:** `https://hf-invoice-generator.vercel.app`
- **Minimum Android Version:** Android 7.0 (API 24)
- **Target / Compile SDK:** Android 16 (API 36)

---

## Architecture & Security

This application wraps the live production Next.js invoicing system in a hardened native Android WebView hosted inside a branded Jetpack Compose interface. All customer records, invoices, GST calculations, and payments remain strictly on the existing hosted backend — no duplicate database, no Firebase backend, and no credentials or secrets are stored in the APK.

### Security Controls
- **HTTPS-Only Policy:** Enforced via `network_security_config.xml` (`cleartextTrafficPermitted="false"`) and `TrustedOriginPolicy`.
- **Trusted Origin Allowlist:** Only `https://hf-invoice-generator.vercel.app` loads inside the WebView. Unrelated HTTPS links, `mailto:`, and `tel:` links open externally in the Android system browser or dialer; insecure schemes (`http:`, `file:`, `javascript:`, `content:`) are blocked.
- **Strict SSL Validation:** `onReceivedSslError` always cancels the request and displays a security warning — SSL errors are never ignored.
- **Hardened WebView:** Safe Browsing enabled, third-party cookies disabled, `allowFileAccess = false`, `allowContentAccess = false`, mixed content disallowed, and no JavaScript bridges (`addJavascriptInterface` is not used).
- **Authenticated PDF & CSV Downloads:** Session cookies are read in memory from WebView's `CookieManager` only for verified HTTPS requests to the trusted host and are never logged or written to storage. Cloud backup of app data is disabled (`allowBackup="false"`).
- **Zero-Permission Storage & Uploads:** Uses `MediaStore.Downloads` (Android 10+) and Android's zero-permission Photo Picker (`PickVisualMedia`) for company logo uploads without requesting broad storage permissions.

### Native Enhancements
- Branded splash screen and custom adaptive launcher icon using the official HF Removals Adelaide logo.
- Forest green (`#073D2F`), gold (`#C69B49`), and white Material 3 interface with bundled `Playfair Display` and `Plus Jakarta Sans` typography.
- Native pull-to-refresh (active only at the top of the page), Back button history navigation, keyboard-safe `adjustResize` + IME insets, and rotation state preservation.
- Live network monitoring and branded connection-loss / SSL recovery screen with **Retry**.
- Native A4 PDF downloading (preserving original invoice filenames like `HF-Invoice-....pdf`), download-complete notifications, native Android **Share** sheet, **Open**, and multi-page **PrintManager** (ISO A4) support, plus a **Saved Invoice Documents** drawer.

---

## Exporting & Building in Android Studio

1. Export the project as a ZIP from Google AI Studio (or clone from GitHub).
2. Extract the ZIP and open the root folder in **Android Studio** (JDK 21 / Android SDK 36).
3. Wait for Gradle Sync to complete.
4. Run unit tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```
5. Build a debug APK:
   ```bash
   gradle :app:assembleDebug
   ```
   Output APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## Generating a Signed Release APK and Android App Bundle (AAB)

Never commit your release keystore or passwords to Git.

### Option A: Using Android Studio (Recommended)
1. Open the project in **Android Studio**.
2. Go to **Build → Generate Signed App Bundle / APK…**
3. Choose **Android App Bundle** (for Google Play) or **APK** (for direct private installation), then click **Next**.
4. Under **Key store path**, click **Create new…** (first time) or select your existing `.jks` keystore file:
   - Store the `.jks` file in a secure backup location outside this repository.
   - Enter a strong keystore password, key alias (e.g. `hf-removals-release`), key password, and organization details (`HF Removals Adelaide`, `AU`).
5. Click **Next**, select the **release** build variant, and click **Create**.
6. Locate the signed outputs:
   - Signed AAB: `app/release/app-release.aab`
   - Signed APK: `app/release/app-release.apk`

### Option B: Command-Line Keystore + Gradle
1. Generate a private upload keystore once:
   ```bash
   keytool -genkeypair -v \
     -keystore ~/hf-removals-release.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias hf-removals-release
   ```
2. Add a `release` signing config in `app/build.gradle.kts` reading credentials from environment variables (or a git-ignored `keystore.properties` file).
3. Run:
   ```bash
   gradle :app:assembleRelease   # Signed release APK
   gradle :app:bundleRelease     # Signed release AAB
   ```

---

## Installing on a Physical Android Phone

### Method 1: Direct APK Install (Private Business Distribution)
1. Transfer the signed `app-release.apk` (or `app-debug.apk` for internal testing) to the Android phone via USB, Google Drive, or email.
2. Open the **Files** app on the Android phone and tap the `.apk` file.
3. If prompted with *"For your security, your phone isn't allowed to install unknown apps from this source"*, tap **Settings → Allow from this source**, then return and tap **Install**.
4. Launch **HF Removals Invoices** from the home screen and sign in using the administrator password.

### Method 2: Install via USB with ADB
1. On the Android phone, enable **Developer options** (*Settings → About phone → tap Build number 7 times*) and turn on **USB debugging**.
2. Connect the phone to your computer via USB and approve the RSA debugging prompt on the phone.
3. Run:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   (Or point to your signed `app-release.apk`.)

### Method 3: Private Google Play Distribution (AAB)
Upload `app-release.aab` to **Google Play Console → Internal testing** (or Managed Google Play for private business devices) to distribute updates automatically with Google Play signing.
