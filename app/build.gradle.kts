plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.chanelbox.l1"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.chanelbox.l1"
        minSdk = 29          // Android 10+ (tải file về thư mục Downloads không cần xin quyền)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
