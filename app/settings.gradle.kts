// 账然 v0.1 工程设置
//
// 工程根目录 = app/（用户决策：所有代码文件集中在 app/ 下，工作区根目录不放代码）。
// v0.1 只有一个模块，即本工程本身，**无子项目**（依据 docs/04 §3 末「v0.1 不拆模块」）。
// 版本目录位于 app/gradle/libs.versions.toml，Gradle 会自动识别。
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "zhangran"
