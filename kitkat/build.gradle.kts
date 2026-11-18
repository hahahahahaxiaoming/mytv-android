plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "top.yogiczy.mytv.kitkat"
    compileSdk = 34
    defaultConfig {
        applicationId = "top.yogiczy.mytv.kitkat"
        minSdk = 19
        targetSdk = 34
        versionCode = 1
        versionName = "1.4.4-kitkat"
    }
    sourceSets["main"].java.srcDirs(
        "../shared/src/main/java",
        "../app/src/main/java/top/yogiczy/mytv/data/repositories/iptv/parser",
    )
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
