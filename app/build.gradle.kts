plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 注意：不要命名为 compileSdkVersion / minSdkVersion，
// 会与 AndroidExtension 上的同名（已废弃）属性冲突。
val sdkMin = 29
val sdkTarget = 35
val sdkCompile = 35

// ---- 签名与版本号 ----
// CI（.github/workflows/release.yml）会用环境变量注入正式 keystore；没有就退回 debug 签名。
// 不再产出 unsigned 包：unsigned 的 release APK 在真机上根本装不上，
// "Releases 里挂一个装不了的包"没有意义。
val envKeystore = System.getenv("LW_KEYSTORE_PATH")
val envVersionCode = System.getenv("LW_VERSION_CODE")?.toIntOrNull()
val envVersionName = System.getenv("LW_VERSION_NAME")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.livingwallpaper.app"
    compileSdk = sdkCompile

    defaultConfig {
        applicationId = "com.livingwallpaper.app"
        minSdk = sdkMin
        targetSdk = sdkTarget
        // 默认值给本地构建用；CI 按 tag 覆盖（v0.2.0 → 0.2.0 / run 号当 versionCode）
        versionCode = envVersionCode ?: 1
        versionName = envVersionName ?: "0.1.0-p1"

        buildConfigField("int", "MIN_SDK_VERSION", "$sdkMin")
        buildConfigField("int", "TARGET_SDK_VERSION", "$sdkTarget")
        buildConfigField("int", "COMPILE_SDK_VERSION", "$sdkCompile")
    }

    signingConfigs {
        if (envKeystore != null) {
            create("release") {
                storeFile = file(envKeystore)
                storePassword = System.getenv("LW_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LW_KEY_ALIAS")
                keyPassword = System.getenv("LW_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (envKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            // Release 产物要挂到 GitHub Releases 给人装，包体不能放任：
            // 不开裁剪时 classes.dex 有 40MB（Material Icons 全量），装出来 43MB。
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
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":renderer"))
    implementation(project(":wallpaper"))
    implementation(project(":ai"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
