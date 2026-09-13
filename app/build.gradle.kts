plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.autocontrol"
    compileSdk = 35

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
    // Compose BOM 2025.06.00 对应 Compose 1.8（要求 Kotlin 2.2，与 AGP 9.0 内置 Kotlin 一致）。
    // 注意：Compose BOM 只管理 androidx.compose.* 组（ui / material3 / material-icons 等）。
    // navigation / activity / lifecycle / core / test 等 Companion 库不在 BOM 内，必须显式写版本。
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose（由 BOM 管理版本，无需写版本号）
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Companion 库：显式版本（不在 Compose BOM 内）
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // DataStore（不在 Compose BOM 内）
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Tests（显式版本；ui-test-junit4 / ui-tooling / ui-test-manifest 属 androidx.compose.* 由 BOM 管理）
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
