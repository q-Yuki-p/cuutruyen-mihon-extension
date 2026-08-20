import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// ----------------------------------------------------------------------------------
// Extension metadata (mirrors the pattern used by Tachiyomi/Mihon community sources)
// ----------------------------------------------------------------------------------
val extName = "Cứu Truyện"
val pkgNameSuffix = "vi.cuutruyen"
val extClass = ".CuuTruyen"
val extVersionCode = 1
val libVersion = "1.6"

// JitPack artifact version — tachiyomix README says "1.6" but the actual
// published tag on JitPack is "1.6.0"; "1.6" resolves to 404.
val tachiyomixVersion = "1.6.0"

android {
    namespace = "eu.kanade.tachiyomi.extension.$pkgNameSuffix"
    compileSdk = 34

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.$pkgNameSuffix"
        minSdk = 21
        targetSdk = 34
        versionCode = extVersionCode
        versionName = "$libVersion.$extVersionCode"

        manifestPlaceholders["appName"] = "$extName"
        manifestPlaceholders["extClass"] = extClass
        manifestPlaceholders["contentWarning"] = "2" // 0=Safe, 1=Mixed, 2=NSFW (tachiyomix 1.6 scale)
        manifestPlaceholders["extensionLib"] = libVersion
    }

    // Release signing: values come from a local keystore.properties file (not committed)
    // or from CI environment variables injected by .github/workflows/build.yml
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    var hasReleaseKeystore = false
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(keystorePropertiesFile.inputStream())
        hasReleaseKeystore = true
    } else if (!System.getenv("CI_KEYSTORE_PATH").isNullOrEmpty()) {
        keystoreProperties["storeFile"] = System.getenv("CI_KEYSTORE_PATH")
        keystoreProperties["storePassword"] = System.getenv("CI_KEYSTORE_PASSWORD")
        keystoreProperties["keyAlias"] = System.getenv("CI_KEY_ALIAS")
        keystoreProperties["keyPassword"] = System.getenv("CI_KEY_PASSWORD")
        hasReleaseKeystore = true
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // A persistent release keystore is required, not optional. Falling back
            // to Gradle's auto-generated debug keystore silently would mean every CI
            // run signs with a *different* certificate, and Android refuses to install
            // a same-package "update" whose certificate doesn't match the one already
            // on the device -- every user would need to uninstall+reinstall on every
            // release, losing their reading settings each time. See README.md.
            if (!hasReleaseKeystore) {
                throw GradleException(
                    "No release keystore configured. Create keystore.properties (local dev) " +
                        "or set CI_KEYSTORE_PATH/CI_KEYSTORE_PASSWORD/CI_KEY_ALIAS/CI_KEY_PASSWORD " +
                        "(CI) before running assembleRelease. See README.md \"Tạo keystore ký release\".",
                )
            }
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Stub interfaces only -- the real implementations live inside the host app (Mihon /
    // Suwayomi-Server). Versions below are copied 1:1 from tachiyomix's own documented
    // "App Dependency Requirements" table (github.com/mihonapp/tachiyomix, README.md) --
    // the exact compileOnly set the library itself specifies for 1.6 compatibility.
    compileOnly("com.github.mihonapp:tachiyomix:$tachiyomixVersion")
    compileOnly("com.github.mihonapp:injekt:91edab2317")
    compileOnly("com.squareup.okhttp3:okhttp:5.4.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    compileOnly("org.jsoup:jsoup:1.22.2")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
}
