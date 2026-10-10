# app —— 账然 Android 工程根目录

状态：**T1a 已完成**（Gradle 工程可编译 + Compose/Hilt/KSP/Room 构建链打通）。

**本目录就是 Gradle 工程根**（用户决策：所有代码集中在 `app/` 下，工作区根目录不放代码）。
v0.1 只有这一个工程，**无子项目**（依据 `docs/04` §3 末「v0.1 不拆模块」）。

## 构建

```bash
cd app
./gradlew assembleDebug testDebugUnitTest        # Windows: .\gradlew.bat assembleDebug testDebugUnitTest
```

**构建前置（重要）**

- **构建期 JVM 必须是 JDK 21+**。原因：Hilt 生成的 Java 编译任务（`hiltJavaCompile*`）不跟随 Gradle toolchain，直接用构建期 JVM 编译，JDK 17 会报 `release 21 不受支持`。
- 本机可用的 JDK 21：Android Studio 自带 JBR（`C:\Program Files\Android\Android Studio\jbr`）。把它设为构建期 JVM 的方式（任选其一）：
  - 临时：`$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'`（Windows PowerShell）
  - 持久：把 `JAVA_HOME` 指向任一 JDK 21+ 安装
  - IDE：Android Studio 的 Gradle JDK 设为其自带 JBR
- **工程内不再硬编码 `org.gradle.java.home`**（硬编码会让换机器 / CI 直接失败）。Java 21 的意图由 `build.gradle.kts` 的 `java { toolchain { languageVersion = 21 } }` 声明，用于 IDE / 工具链解析与明确报错。

其他约束：

- 版本唯一来源：`gradle/libs.versions.toml`（构建脚本禁止出现字面版本号）。
- `applicationId` 固定 `com.zhangran.app`；`versionName` `0.1.0` / `versionCode` `1`（同样定义在版本目录）。

## 目录

```text
app/                                ← Gradle 工程根
├── settings.gradle.kts             # 单工程，无 include
├── build.gradle.kts                # 插件、Android 配置、KSP、依赖
├── gradle.properties               # JVM / AndroidX / Kotlin 开关（不含版本号）
├── gradlew / gradlew.bat           # wrapper 脚本
├── gradle/
│   ├── libs.versions.toml          # 版本目录（唯一版本来源）
│   └── wrapper/                    # wrapper jar + properties（Gradle 9.8.1）
├── proguard-rules.pro              # 空占位（当前 release 不混淆）
├── schemas/                        # Room schema 导出目录（尚无实体，待 T2）
└── src/
    ├── main/
    │   ├── AndroidManifest.xml     # 零采集类权限与 INTERNET（docs/07 §9.1）
    │   ├── java/com/zhangran/app/
    │   │   ├── ZhangRanApp.kt      # @HiltAndroidApp 应用类
    │   │   └── MainActivity.kt     # 单 Activity，仅显示“账然”文本
    │   └── res/values/             # strings.xml / themes.xml（最小资源，无 token）
    ├── debug/AndroidManifest.xml   # 移除依赖带入的 exported=true 组件（PreviewActivity / ProfileInstallReceiver）
    └── test/java/com/zhangran/app/BuildConfigTest.kt
```

## 构建基线（T1a 落地值）

| 项 | 值 |
|---|---|
| AGP / Gradle | 9.4.1 / 9.8.1 |
| Kotlin | AGP 9 内建（**不使用** `org.jetbrains.kotlin.android`）；Compose 编译器插件 2.3.21 |
| KSP / Hilt / Room | 2.3.12 / 2.60.1 / 2.8.4 |
| JDK | 21（**构建期 JVM 必须 21+**，见上） |
| `compileSdk` / `targetSdk` / `minSdk` | **37** / 36 / 31 |
| Compose BOM | 2026.09.00 |

`compileSdk = 37` 是依赖侧硬要求（AndroidX/Compose 2026.09 要求依赖方 compile 到 API ≥ 37，见 `docs/09` §3）；`targetSdk` 保持 36。

## 尚未创建（属后续任务）

- **T1b**：包结构（`ui/theme`、`ui/components`、`ui/navigation`、`ui/screen/…`、`domain/`、`data/`、`capture/`、`autoaccounting/`、`backup/`、`log/`、`common/`）、设计 token、导航骨架与 18 个占位页面。包结构的详细指导见 T1b 任务书。
- **T2**：Room 实体与 DAO（届时首次产出 `schemas/**.json`）。

## 约束（不变）

- 版本唯一来源为 `gradle/libs.versions.toml`，构建脚本不得出现字面版本号。
- `applicationId` 固定为 `com.zhangran.app`；版本号规则见任务书 §4.1.1（人工置 `1.0.0`，Agent 不得自行判定完成）。
- 账然自身不申请任何采集类权限与 `INTERNET`（采集能力来自上游独立应用）。
- 界面不得出现字面颜色/尺寸（token 属 T1b，依据 `docs/05` §3–§6）。
