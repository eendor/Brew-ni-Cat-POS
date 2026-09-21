plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.cattasticpos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.cattasticpos"
        minSdk = 26
        targetSdk = 34
        versionCode = 10133
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Stable signing key committed at app/keystore/release.jks. In-place auto-updates
        // require every build to carry the SAME signature; relying on the per-machine
        // ~/.android/debug.keystore broke updates once when that key was regenerated, so
        // both debug and release builds are pinned to this one key from here on.
        create("brewnicat") {
            storeFile = file("keystore/release.jks")
            storePassword = "brewnicat"
            keyAlias = "brewnicat"
            keyPassword = "brewnicat"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("brewnicat")
        }
        release {
            // Shipped builds are now assembleRelease, not assembleDebug: debuggable=true was
            // costing real frame time in Compose on the Redmi Pad 2 (ART disables several
            // optimizations for debuggable apps). R8 shrinks the APK further for faster
            // over-the-air updates; obfuscation is disabled in proguard-rules.pro so
            // CrashActivity stack traces stay readable.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("brewnicat")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    // material-icons-extended (~thousands of icons) was pulled in for just Delete + Share in
    // HistoryScreen — both live in the core set bundled with material3 — so it's dropped to keep
    // the APK small and updates fast on weak mobile data.
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.coroutines.android)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Haze liquid glass blur
    implementation(libs.haze)

    // Lucide thin-stroke icons
    implementation(libs.icons.lucide.android)

    // OkHttp
    implementation(libs.okhttp)

    // Unit tests for the pure pricing / recipe / sync-id logic
    testImplementation(libs.junit)
    testImplementation(libs.json.unit.test)

    // Compose Unstyled (requires Kotlin 2.3+ KSP — wrappers in ui/components/unstyled until toolchain catches up)
    // implementation(libs.compose.unstyled.theming)
    // implementation(libs.compose.unstyled.button)
    // implementation(libs.compose.unstyled.text.field)
    // Compose Cupertino (KMP artifacts — local adaptive layer in ui/adaptive mirrors API on AndroidX Compose)
    // implementation(libs.compose.cupertino)
    // implementation(libs.compose.cupertino.adaptive)
    // implementation(libs.compose.cupertino.native)

}
