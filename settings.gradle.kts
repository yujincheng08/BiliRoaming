include(":app")
buildCache { local { removeUnusedEntriesAfterDays = 1 } }
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        val kotlinVersion: String by settings
        id("com.android.application") version "7.4.1" apply false
        id("org.jetbrains.kotlin.android") version kotlinVersion apply false
        id("com.google.protobuf") version "0.9.1" apply false
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://api.xposed.info")
    }
}
rootProject.name = "BiliRoaming"
