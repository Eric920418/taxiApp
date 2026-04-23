// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false

    // Google Services plugin for Firebase
    id("com.google.gms.google-services") version "4.4.4" apply false

    // Firebase App Distribution plugin
    id("com.google.firebase.appdistribution") version "5.1.1" apply false

    // Google Play Publisher (Triple-T) — 自動上傳 AAB 到 Play Console
    id("com.github.triplet.play") version "3.12.1" apply false
}