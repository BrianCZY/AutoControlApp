# 自动化控制 (AutoControlApp)

一个深色主题的 Android 原生「自动化控制」App，原型来自 Figma Make。

## 功能

- **首页**
  - 定时关机（开关 + 关机时间）
  - 时段屏幕变灰（开关 + 起止时间选择）
  - 定时断网（开关 + 起止时间段）
- **设置**
  - 核心权限（电池优化、前台服务）
  - 附加权限（辅助功能、通知）
  - 前台服务状态卡 + 服务元信息
- 底部 Tab 切换首页 / 设置
- 全部状态通过 DataStore 持久化，下次启动自动恢复
- **真实系统集成**（前台服务 + AlarmManager 定时调度 + 辅助功能灰度 + 关机/断网）

## 系统集成能力

| 能力 | 实现 | 普通设备可用性 |
| --- | --- | --- |
| 灰度模式 | 辅助功能服务 + `Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER` | ✅ 真实可用 |
| 前台服务 | `ForegroundService` + 前台通知 + `AlarmManager` | ✅ 真实可用 |
| 定时调度 | `setExactAndAllowWhileIdle` + `SCHEDULE_EXACT_ALARM` + 开机自启 | ✅ 真实可用 |
| 关机 | 反射 `PowerManager.shutdown()`（隐藏 API） | ⚠️ 普通设备受限，会诚实降级提示 |
| 断网 | `WifiManager.setWifiEnabled()`（API 10+ 返回 false） | ⚠️ 普通设备受限，会诚实降级提示 |

> **说明**：关机与断网在**普通设备（无 root）**上受 Android 平台安全限制，无法真正生效。
> 代码已做「诚实降级」——尝试执行，失败时通过通知明确告知用户需要手动操作或系统权限。
> 灰度模式、前台服务、定时调度在普通设备上真实可用。

## 技术栈

- Kotlin 1.9.22
- Jetpack Compose（BOM 2024.02.00，Material3）
- Navigation Compose 2.7.7
- DataStore Preferences 1.0.0
- minSdk 26，targetSdk/compileSdk 34，JVM 17
- AGP 8.2.2 + Gradle 8.4

## 目录结构

```
AutoControlApp/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties   # 需执行 `gradle wrapper` 生成 jar/脚本
├── README.md
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── values/{strings,colors,themes}.xml
        │   ├── xml/accessibility_service_config.xml   # 辅助功能服务配置
        │   └── ...
        └── java/com/example/autocontrol/
            ├── AutoControlApplication.kt
            ├── MainActivity.kt
            ├── nav/AppNav.kt
            ├── data/
            │   ├── ControlState.kt
            │   ├── PermissionManager.kt        # 权限检查/跳转
            │   └── SettingsRepository.kt
            ├── service/
            │   ├── AutoControlService.kt        # 前台服务
            │   ├── ActionExecutor.kt            # 关机/灰度/断网动作
            │   ├── Scheduler.kt                 # AlarmManager 调度
            │   ├── BootReceiver.kt              # 开机自启
            │   ├── GrayscaleAccessibilityService.kt  # 辅助功能服务
            │   └── NotificationHelper.kt        # 通知
            └── ui/
                ├── theme/{Color,Type,Theme}.kt
                ├── components/...
                ├── home/{HomeScreen,HomeViewModel,LocalAppContext}.kt
                └── settings/{SettingsScreen,SettingsViewModel}.kt
```

## 本地构建

环境要求：JDK 17、Android SDK Platform 34、本地有 `gradle` 命令（或 Android Studio Hedgehog+）。

```bash
# 1. 首次需要生成 wrapper（一次性）
cd AutoControlApp
gradle wrapper

# 2. 构建 debug APK
./gradlew :app:assembleDebug

# 3. 安装到设备/模拟器
./gradlew :app:installDebug
```

或者直接用 Android Studio 打开 `AutoControlApp` 根目录即可。

## 真机使用前必读

1. **灰度模式**：需在系统「设置 → 辅助功能」中开启「自动化控制 · 灰度模式」服务。
2. **定时任务**：Android 12+ 需授权「闹钟与提醒」（精确闹钟权限）；Android 6+ 建议将应用加入电池优化白名单。
3. **通知**：Android 13+ 首次使用需授予通知权限。
4. **关机/断网**：普通设备无法真正执行，App 会通知你手动操作。

## 沙箱环境说明

本仓库源码由具备 Android SDK + JDK 17 的开发机直接构建。
云端沙箱（无 `dl.google.com` 网络访问、无 Android SDK）无法运行 `./gradlew` 验证。
