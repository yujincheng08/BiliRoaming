include(":app")
buildCache { local { removeUnusedEntriesAfterDays = 1 } }
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
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
