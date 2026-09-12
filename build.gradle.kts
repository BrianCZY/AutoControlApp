// Top-level build file
// 注意：AGP 9.0 已内置 Kotlin 支持，无需再 apply org.jetbrains.kotlin.android，
// 否则会与 AGP 注册的 kotlin 扩展冲突（Cannot add extension with name 'kotlin'）。
// AGP 9.0 强制内置 Kotlin 2.2.10，因此 Compose 编译器插件需对齐到 2.2.10。
plugins {
    id("com.android.application") version "9.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
