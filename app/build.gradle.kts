import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

fun loadReleaseSigning(): Map<String, String>? {
    val fromFile = rootProject.file("keystore.properties")
        .takeIf { it.exists() }
        ?.let { file -> Properties().also { props -> file.inputStream().use { props.load(it) } } }
    fun value(prop: String, env: String): String? =
        fromFile?.getProperty(prop) ?: System.getenv(env)
    val store = value("storeFile", "HF_RELEASE_STORE_FILE")
    val storePass = value("storePassword", "HF_RELEASE_STORE_PASSWORD")
    val alias = value("keyAlias", "HF_RELEASE_KEY_ALIAS")
    val keyPass = value("keyPassword", "HF_RELEASE_KEY_PASSWORD")
    if (store == null || storePass == null || alias == null || keyPass == null) return null
    return mapOf(
        "storeFile" to store,
        "storePassword" to storePass,
        "keyAlias" to alias,
        "keyPassword" to keyPass,
    )
}

android {
    namespace = "com.hfremovalsadelaide.invoices"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.hfremovalsadelaide.invoices"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "2.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Release signing is read from a git-ignored keystore.properties file or from
    // HF_RELEASE_* environment variables. Debug builds use the standard Android debug key
    // that the Android Gradle Plugin generates automatically (~/.android/debug.keystore).
    val releaseSigning = loadReleaseSigning()
    if (releaseSigning != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseSigning.getValue("storeFile"))
                storePassword = releaseSigning.getValue("storePassword")
                keyAlias = releaseSigning.getValue("keyAlias")
                keyPassword = releaseSigning.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // One persistent key for every build (local and CI) so updates always install over the
            // existing app. When no signing details are provided, Android's default debug key is used,
            // which on a developer machine is the same key that was stored as the CI secret.
            if (releaseSigning != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // QA copy of the debug app with its OWN package name (...invoices.qa). Instrumented tests target this copy,
        // so installing, running or uninstalling them can never touch the real app or its data.
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            matchingFallbacks += listOf("debug")
        }
        release {
            if (releaseSigning != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // java.time on Android 7 (minSdk 24)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        // Test flows and fakes shared by the JVM (Robolectric) tests and the on-device tests.
        getByName("test").kotlin.directories.add("src/sharedTest/java")
        getByName("androidTest").kotlin.directories.add("src/sharedTest/java")
    }

    testBuildType = "qa"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.zxing.core)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    "qaImplementation"(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.work.testing)
}
