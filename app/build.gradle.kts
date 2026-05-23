import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// ===== 版本管理 =====
val versionFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionFile.exists()) load(versionFile.inputStream())
}
val versionCode = (versionProps["VERSION_CODE"] ?: "1").toString().toInt()
val versionName = (versionProps["VERSION_NAME"] ?: "1.0.0").toString()

// ===== 签名配置 =====
val keystoreFile = rootProject.file("keystore.properties")
val signingConfigsMap = mutableMapOf<String, String>()
if (keystoreFile.exists()) {
    Properties().apply {
        load(keystoreFile.inputStream())
        signingConfigsMap["storeFile"] = getProperty("storeFile", "")
        signingConfigsMap["storePassword"] = getProperty("storePassword", "")
        signingConfigsMap["keyAlias"] = getProperty("keyAlias", "")
        signingConfigsMap["keyPassword"] = getProperty("keyPassword", "")
    }
}

// CI 环境变量覆盖（GitHub Secrets）
val ciStoreFile = System.getenv("KEYSTORE_FILE") ?: signingConfigsMap["storeFile"]
val ciStorePassword = System.getenv("KEYSTORE_PASSWORD") ?: signingConfigsMap["storePassword"]
val ciKeyAlias = System.getenv("KEY_ALIAS") ?: signingConfigsMap["keyAlias"]
val ciKeyPassword = System.getenv("KEY_PASSWORD") ?: signingConfigsMap["keyPassword"]

android {
    namespace = "com.egoflow.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.egoflow.app"
        minSdk = 26
        targetSdk = 34
        versionCode = versionCode
        versionName = versionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // 仅在 keystore 存在时创建 release 签名配置
        val releaseStoreFile = if (ciStoreFile != null && ciStoreFile.isNotEmpty()) {
            if (ciStoreFile.startsWith("/") || ciStoreFile.contains(":")) {
                file(ciStoreFile)
            } else {
                rootProject.file(ciStoreFile)
            }
        } else null
        if (releaseStoreFile != null && releaseStoreFile.exists()) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = ciStorePassword
                keyAlias = ciKeyAlias
                keyPassword = ciKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 如果有 release 签名配置则使用，否则回退到 debug 签名
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        debug {
            // debug 使用 Android Studio 的默认 debug 签名
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.7"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // APK 文件名自定义
    android.applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                val fileName = "EgoFlow-v${variant.versionName}-build${variant.versionCode}-${variant.name}.apk"
                output.outputFileName = fileName
            }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.01.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Activity & Lifecycle
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Retrofit + OkHttp (AI API calls)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
