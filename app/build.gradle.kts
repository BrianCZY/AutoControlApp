plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.autocontrol"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.autocontrol"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// AGP 9.0 内置 Kotlin：jvmTarget 也可不写（AGP 会按 compileOptions.targetCompatibility 自动对齐到 17），
// 这里显式声明以保持与旧配置一致。
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose BOM 2025.06.00 对应 Compose 1.8，要求 Kotlin 2.2（与 AGP 9.0 内置 Kotlin 一致）。
    // 由 BOM 管理版本后，下方 Compose / AndroidX 相关依赖不再写版本号，避免与 BOM 拉取的版本冲突。
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core / Lifecycle
    implementation("androidx.core:core-ktx")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
    implementation("androidx.lifecycle:lifecycle-runtime-compose")

    // Activity / Compose
    implementation("androidx.activity:activity-compose")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose")

    // DataStore（不在 Compose BOM 内，保留显式版本）
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit")
    androidTestImplementation("androidx.test.espresso:espresso-core")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
