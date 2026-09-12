# 自动化控制 App 源码说明

本目录 `/workspace/AutoControlApp/` 是一个完整的 **Android 原生 + Jetpack Compose** 工程，
通过 `agent-browser` 抓取 `https://bear-dog-44038655.figma.site/` 原型后，
按还原度从高到低完成：

- 首页「自动化控制」：定时关机 / 时段屏幕变灰 / 定时断网 三张卡片 + 自绘 Switch + Material3 TimePicker
- 设置页「权限与服务」：告警条、核心/附加权限分组、状态卡 + 服务元信息
- 全部状态通过 DataStore Preferences 持久化
- 深色主题、薄荷绿强调色

## 本地构建

```bash
cd /workspace/AutoControlApp
gradle wrapper                     # 一次性：生成 gradlew / gradle-wrapper.jar
./gradlew :app:assembleDebug       # 构建 debug APK
```

要求：JDK 17、Android SDK Platform 34、本机有 `gradle` 命令行（首次需联网下载 Android Gradle Plugin）。

## 不在范围内

- 真实前台服务 / AlarmManager / 辅助功能集成
- 运行时权限跳系统设置 / 电池优化白名单
- 单元/UI 测试
