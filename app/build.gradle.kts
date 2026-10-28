// 账然 v0.1 构建脚本（工程根 = app/，单模块，无子项目）
//
// 职责：把 Compose / Hilt / KSP / Room(仅约定) 四条构建链打通。
// 版本一律经 libs.* 引用（app/gradle/libs.versions.toml 是唯一来源）。
// 本任务不建任何 Room 实体、不建 UI 主题与页面（属 T1b）。
//
// 关于 Kotlin 支持：**AGP 9.0 起内建 Kotlin**，`org.jetbrains.kotlin.android`
// 插件必须移除（否则报 “The 'org.jetbrains.kotlin.android' plugin is no longer
// required for Kotlin support since AGP 9.0”）。Kotlin 版本由 AGP 9.4.1 内建决定。
// 关于 KSP 与 Hilt（Dagger issue #3965）：二者必须在同一 scope 应用；
// 本工程只有一个工程（无子项目），因此天然同 scope。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = libs.versions.applicationId.get()
    // compileSdk 37：AndroidX/Compose 2026.09 要求依赖方至少 compile 到 API 37
    // （构建日志："requires ... to compile against version 37 or later"）。
    // targetSdk 保持 36（任务书要求），运行时行为仍按 API 36。
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = libs.versions.applicationId.get()
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // 版本号唯一来源：app/gradle/libs.versions.toml（任务书 §4.1.1）
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        // T2a（已由人工授权的范围外一行）：MigrationTestHelper 只从 **assets** 读取导出的
        // schema JSON（见 room-testing 源码 MigrationTestHelper.android.kt 的 loadSchema）：
        //   context.assets.open("$assetsFolder/$version.json")
        // 因此必须把 app/schemas 也作为 androidTest 的 assets 源目录，否则 A9 必然报
        // "Cannot find the schema file in the assets folder"。
        // 注意：这不是依赖声明，而是 assets 源目录配置。
        // 用 `directories`（新 API）而不是已弃用的 `srcDir(...)`，避免 Gradle 10 不兼容。
        getByName("androidTest").assets.directories.add(
            layout.projectDirectory.dir("schemas").asFile.absolutePath,
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        // Java 21。注意：这里必须显式声明，不能只依赖下面的 java { toolchain }——
        // Hilt 生成的 Java 编译任务（hiltJavaCompile*）不跟随 toolchain 属性，会用构建期
        // JVM 编译；因此**构建期 JVM 必须是 JDK 21+**（见 app/README.md 的“构建前置”）。
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 收紧 debug 依赖带入的合并 Manifest 组件：
    // compose-ui-tooling 会带入 androidx.compose.ui.tooling.PreviewActivity(exported=true)，
    // profileinstaller 会带入 androidx.profileinstaller.ProfileInstallReceiver(exported=true)。
    // 任务书 §4.3 要求「不得声明任何 exported="true" 的组件」（唯一例外是 T4 的 scheme 接收入口），
    // 因此由 src/debug/AndroidManifest.xml 以 tools:node="remove" 显式移除；
    // 注意不能设 dependenciesInfo.includeInApk=false（APK 缺少依赖元数据会无法安装）。
}

kotlin {
    compilerOptions {
        // 与 compileOptions 保持一致（统一 JDK 21）
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// Java 21 由 Gradle Toolchain 声明，而不是在 gradle.properties 硬编码本机 JDK 路径
// （硬编码会让换机器 / CI 直接失败）。
// 注意（T1a 实测结论）：Hilt 生成的 Java 编译任务不跟随 toolchain，因此**构建期 JVM
// 仍必须是 JDK 21+**；此声明保证 IDE/工具链解析与编译级版本一致，并在 JVM 版本不足时
// 给出明确报错，而不是静默降级。
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Room 只落地 schema 导出约定，本任务不建实体（依据 docs/04 §7、docs/08 §9 前置）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // AndroidX 基础
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose（版本由 BOM 统一管理）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation Compose（T1b 追加：单 Activity + 导航骨架）
    implementation(libs.androidx.navigation.compose)

    // Hilt（本任务必须打通）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // DataStore：仅存设置项（主题/置信度阈值），不存账务数据（M4c §4.1）
    implementation(libs.androidx.datastore.preferences)

    // Room（只引入依赖，不建实体）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // T2a：对齐 kotlinx-serialization-core 版本。
    // 背景：room-migration（经 room-testing 进入）用 kotlinx-serialization-json 1.8.1 反序列化
    // schema JSON，需要 core ≥ 1.8；而 navigation/lifecycle 的传递依赖把 core 约束在 1.7.3，
    // 且 AGP 的 consistent resolution 会把 androidTest 的版本绑到主运行时类路径解析结果，
    // 于是运行期抛 AbstractMethodError: GeneratedSerializer.typeParametersSerializers。
    // 这里提升到与 json 同版（1.8.1）；本应用自身不含序列化业务代码，属安全的版本对齐。
    implementation(libs.kotlinx.serialization.core)
    // M4b-1：备份 JSON（唯一新增运行时依赖；版本与 core 一致 1.8.1）
    implementation(libs.kotlinx.serialization.json)

    // 单元测试
    testImplementation(libs.junit)

    // instrumentation 测试（T1b：18 页面渲染冒烟）
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // ui-test-manifest 必须是 **debugImplementation**（进目标 App 的 debug 变体），
    // 它提供 createComposeRule() 所需的宿主 androidx.activity.ComponentActivity 声明。
    // 两个实测坑：
    // - 放 androidTestImplementation → 该 Activity 落到测试 APK 进程，报
    //   “Intent in process com.zhangran.app resolved to different process com.zhangran.app.test”；
    // - 若声明确实缺失，测试会在取 Activity 阶段永久阻塞（无任何测试日志、AM 从不启动 Activity）。
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // T2a：Room 设备侧测试（migration / DAO / 约束）
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
}
