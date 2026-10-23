plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.livingwallpaper.renderer"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    // 着色器源码放在默认位置 src/main/assets/shaders/，无需额外 srcDirs 配置。
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
