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

**真机 / 模拟器验证**

```bash
cd app
./gradlew connectedDebugAndroidTest          # 需要一台已连接且空闲的设备
```

前置（否则会出现“每个用例 60s 超时”或“根本没开始跑”，详见 `docs/09` §5）：

1. **使用 AVD（推荐）**：本机 `Medium_Phone_API_36` 已验证可跑通全部 36 个用例。
2. 若用小米 / HyperOS 真机：需在设备上开启「USB 安装」并登录小米账号授权，否则 ADB 安装会被拒（`INSTALL_FAILED_USER_RESTRICTED`），且该限制**无法用技术绕过**。
3. 跑之前：关闭设备动画（`window/transition/animator_duration_scale` 置 0）、确保设备空闲（前台为桌面、无触摸）、并清理上次残留（`am force-stop com.zhangran.app`）。

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
    │   │   ├── MainActivity.kt     # 单 Activity：主题 + 导航骨架
    │   │   ├── ui/
    │   │   │   ├── theme/          # 设计 token（Color/Typography/Dimens/Shape/Theme）
    │   │   │   ├── navigation/     # Destinations（18 路由）+ ZhangRanNavHost（5 项底栏）
    │   │   │   ├── components/     # 可复用组件（当前仅包说明，待后续任务）
    │   │   │   └── screen/         # 18 个占位页面 + PlaceholderScreen
    │   │   ├── domain/             # 领域层：service/Money、service/TransactionTime（T3）+ 包说明
    │   │   ├── data/               # 数据层（当前仅包说明，待 T2a）
    │   │   ├── capture/            # 上游接收入口（T4）：契约 + 事件 + 编排 + 无界面 Activity
    │   │   ├── autoaccounting/     # 上游结果 Adapter：CaptureSourceMapper（T3）
    │   │   ├── backup/  common/    # 当前仅包说明
    │   │   └── log/                # CaptureLogSink 接口 + InMemory 实现（T4；Room 实现待 T2b）
    │   └── res/values/             # strings.xml / themes.xml（最小资源，无 token）
    ├── debug/AndroidManifest.xml   # 移除依赖带入的 exported=true 组件（PreviewActivity / ProfileInstallReceiver）
    ├── test/java/com/zhangran/app/           # JVM 单测 92 项（BuildConfig / 路由 / token / 金额 / 时间 / 来源映射 / 契约 / 采集编排）
    └── androidTest/java/com/zhangran/app/    # 真机/模拟器冒烟：18 页面 × 明暗主题（36 项）
```

## 上游接收入口（T4 已落地）

契约（`docs/07` §3.5）：

```text
Intent(ACTION_VIEW, "zhangran://capture?v=1&payload=<Base64URL(JSON)>")
```

- 入口：`capture/CaptureEntryActivity` —— **`exported=true` + `BROWSABLE`**（否则上游 `startActivityIfResolvable` 会静默失败），`Theme.NoDisplay` + `noHistory` + `excludeFromRecents`。
- 入口内只做：取 `Intent` 原始字符串 → 交给 `CaptureEventSource` → 立即 `finish()`。**不做**解码/解析/落库/业务判断。
- 版本 `v` 缺失、非法或不为 `1` → 记日志 + 建议 `UNSUPPORTED_NOTIFICATION`，**不解析载荷**。
- 本任务落库走 `log/CaptureLogSink` 接口（当前为 logcat 实现，`TODO(T2b)` 换 Room）。
- **未新增任何权限**（入站 scheme 不需要权限），权限清单仍只有依赖带入的那条非危险签名权限。

设备侧手工验证：

```bash
adb shell am start -a android.intent.action.VIEW -d 'zhangran://capture?v=1&payload=eyJhIjoxfQ'
adb logcat -s ZhangRanCapture
```

## 构建基线（T1a / T1b 落地值）

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

- **T1b 已完成**：包结构、设计 token、导航骨架与 18 个占位页面。
- **T3 已完成**：`domain/service/Money`、`domain/service/TransactionTime`、`autoaccounting/CaptureSourceMapper`。
- **T4 已完成**：`zhangran://capture` 接收入口（见上节）。
- **T2a（Room 基础与第一批实体）**：待 P-004（`CaptureSource` 枚举落点）确认后开工。
- **T2b**：其余实体与 DAO、迁移到 v2、`capture_logs`/`duplicate_decisions`（后者待 P-002/P-003 确认）。

## 约束（不变）

- 版本唯一来源为 `gradle/libs.versions.toml`，构建脚本不得出现字面版本号。
- `applicationId` 固定为 `com.zhangran.app`；版本号规则见任务书 §4.1.1（人工置 `1.0.0`，Agent 不得自行判定完成）。
- 账然自身不申请任何采集类权限与 `INTERNET`（采集能力来自上游独立应用）。
- 界面不得出现字面颜色/尺寸：颜色只能来自 `ui/theme/Color.kt`，尺寸只能来自 `ui/theme/Dimens.kt`（依据 `docs/05` §3–§6）。
- 骨架期的 token 取值为**占位值**，已用 `TODO(视觉)` 标注，待按 `docs/05` 与原型 CSS 变量确定。
