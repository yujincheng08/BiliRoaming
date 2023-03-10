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
    versionCatalogs {
        create("libs") {
            val protobufVersion = version("protobuf", "3.22.0")
            val coroutinesVersion = version("coroutines", "1.6.4")
            val kotlinVersion = version("kotlin", "1.8.10")
            library("xposed", "de.robv.android.xposed:api:82")
            library("cxx", "dev.rikka.ndk.thirdparty:cxx:1.2.0")
            library(
                "protobuf-kotlin", "com.google.protobuf", "protobuf-kotlin-lite"
            ).versionRef(protobufVersion)
            library(
                "protobuf-java", "com.google.protobuf", "protobuf-javalite"
            ).versionRef(protobufVersion)
            library("protobuf-protoc", "com.google.protobuf", "protoc").versionRef(protobufVersion)
            library("kotlin-stdlib", "org.jetbrains.kotlin", "kotlin-stdlib").versionRef(
                kotlinVersion
            )
            library(
                "kotlin-coroutines-android", "org.jetbrains.kotlinx", "kotlinx-coroutines-android"
            ).versionRef(coroutinesVersion)
            library(
                "kotlin-coroutines-jdk", "org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8"
            ).versionRef("coroutines")
            library("androidx-documentfile", "androidx.documentfile:documentfile:1.0.1")
            plugin("kotlin", "org.jetbrains.kotlin.android").versionRef(kotlinVersion)
            plugin("agp-app", "com.android.application").version("7.4.2")
            plugin("protobuf", "com.google.protobuf").version("0.9.1")
            plugin("lsplugin-jgit", "org.lsposed.lsplugin.jgit").version("1.0")
            plugin("lsplugin-resopt", "org.lsposed.lsplugin.resopt").version("1.2")
            plugin("lsplugin-apksign", "org.lsposed.lsplugin.apksign").version("1.1")
            plugin("lsplugin-apktransform", "org.lsposed.lsplugin.apktransform").version("1.2")
            plugin("lsplugin-cmaker", "org.lsposed.lsplugin.cmaker").version("1.0")
        }
    }
}
rootProject.name = "BiliRoaming"
