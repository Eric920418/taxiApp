import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    // Google Services plugin
    id("com.google.gms.google-services")

    // Firebase App Distribution plugin
    id("com.google.firebase.appdistribution")
}

// 讀取 keystore.properties
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// 讀取 local.properties（Maps API Key 等敏感設定，不進 git）
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY") ?: ""

android {
    namespace = "com.hualien.taxidriver"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hualien.taxidriver"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // BuildConfig設定
        buildConfigField("String", "SERVER_URL", "\"https://api.hualientaxi.taxi/api/\"")
        buildConfigField("String", "WS_URL", "\"https://api.hualientaxi.taxi\"")

        // Google Maps API Key 從 local.properties 注入 Manifest（不 hardcode）
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    // AAB split 設定（Play Store 按裝置下載最小 APK）
    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }

    // Signing 配置
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")

            // Firebase App Distribution 配置
            firebaseAppDistribution {
                artifactType = "APK"
                releaseNotes = """
                    v1.1.0 (beta19) — 費率對齊花蓮縣府公告 + rebrand

                    1. 全面 rebrand：花蓮計程車 → GoGoCha（App 名稱 / Logo / About / 通知文案）
                    2. 費率重構對齊花蓮縣府公告：
                       - 日：起跳 100/1000m、每跳 5/230m、低速 120 秒/5 元
                       - 夜 (22:00–06:00)：起跳 100/834m、每跳 5/192m、低速 100 秒/5 元
                       - 春節：admin 設定後全日套夜費率 + 每趟加收 50 元
                    3. 時區修復：計算統一用台北時區（Asia/Taipei），跨時區裝置不再算錯
                    4. Firebase 登入失敗訊息加 actionable hint（SHA-1、配額、reCAPTCHA 提示）
                    5. 司機端結算距離反推公式對齊新費率
                """.trimIndent()
                // 測試者群組（需先在 Firebase Console → App Distribution → Testers & groups 建立此群組並加 testers）
                // groups = "beta-testers"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Firebase BoM (管理所有 Firebase 依賴版本)
    implementation(platform("com.google.firebase:firebase-bom:34.5.0"))

    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth")

    // Firebase Cloud Messaging (推播通知)
    implementation("com.google.firebase:firebase-messaging")

    // Android核心
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Material Icons Extended（包含 PhotoCamera, DirectionsCar, ChevronRight, Help 等）
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // 網路 - Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // 網路 - Socket.io
    implementation("io.socket:socket.io-client:2.1.1") {
        exclude(group = "org.json", module = "json")
    }

    // Google Maps
    implementation("com.google.maps.android:maps-compose:6.2.1")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Kotlin Coroutines for Play Services (用於 await() 扩展函数)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Google Places API (New)
    implementation("com.google.android.libraries.places:places:4.1.0")

    // Google Maps Utils (用於 polyline 解碼等工具)
    implementation("com.google.maps.android:android-maps-utils:3.8.2")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Coil圖片載入
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // Lottie 動畫（用於語音助手角色動畫）
    implementation("com.airbnb.android:lottie-compose:6.6.0")

    // 測試
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
