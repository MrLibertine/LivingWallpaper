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

rootProject.name = "LivingWallpaper"

// P2 起逐步 include：只有真正落地实现的模块才会出现在这里。
include(":app")
include(":core:model")
include(":core:data")
include(":renderer")
include(":wallpaper")
include(":ai")
