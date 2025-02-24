import com.google.protobuf.gradle.*

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.lsplugin.resopt)
    alias(libs.plugins.lsplugin.jgit)
    alias(libs.plugins.lsplugin.apksign)
    alias(libs.plugins.lsplugin.apktransform)
    alias(libs.plugins.lsplugin.cmaker)
}

val appVerCode = jgit.repo()?.commitCount("refs/remotes/origin/master") ?: 0
val appVerName: String by rootProject

apksign {
    storeFileProperty = "releaseStoreFile"
    storePasswordProperty = "releaseStorePassword"
    keyAliasProperty = "releaseKeyAlias"
    keyPasswordProperty = "releaseKeyPassword"
}

apktransform {
    copy {
        when (it.buildType) {
            "release" -> file("${it.name}/BiliRoaming_${appVerName}.apk")
            else -> null
        }
    }
}

cmaker {
    default {
        targets("biliroaming")
        abiFilters("armeabi-v7a", "arm64-v8a", "x86")
        arguments += arrayOf(
            "-DANDROID_STL=none",
            "-DCMAKE_CXX_STANDARD=23",
            "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
        )
        cFlags += "-flto"
        cppFlags += "-flto"
    }

    buildTypes {
        arguments += "-DDEBUG_SYMBOLS_PATH=${layout.buildDirectory.file("symbols/${it.name}").get().asFile.absolutePath}"
    }
}

android {
    namespace = "me.iacn.biliroaming"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    ndkVersion = "27.2.12479018"

    buildFeatures {
        prefab = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "me.iacn.biliroaming"
        minSdk = 24
        targetSdk = 35  // Target Android U
        versionCode = appVerCode
        versionName = appVerName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_11)
        targetCompatibility(JavaVersion.VERSION_11)
    }

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs = listOf(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
            "-language-version=2.0",
        )
    }

    sourceSets {
        named("main") {
            proto {
                srcDir("src/main/proto")
                include("**/*.proto")
            }
        }
    }

    packaging {
        resources {
            excludes += "**"
        }
    }

    lint {
        checkReleaseBuilds = false
    }

    dependenciesInfo {
        includeInApk = false
    }

    androidResources {
        additionalParameters += arrayOf("--allow-reserved-package-id", "--package-id", "0x23")
    }

    externalNativeBuild {
        cmake {
            path("src/main/jni/CMakeLists.txt")
            version = "3.28.0+"
        }
    }
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                id("java") {
                    option("lite")
                }
                id("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

configurations.all {
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk7")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
}

dependencies {
    compileOnly(libs.xposed)
    implementation(libs.protobuf.kotlin)
    implementation(libs.protobuf.java)
    compileOnly(libs.protobuf.protoc)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.coroutines.android)
    implementation(libs.kotlin.coroutines.jdk)
    implementation(libs.androidx.documentfile)
    implementation(libs.cxx)
}

val adbExecutable: String = androidComponents.sdkComponents.adb.get().asFile.absolutePath

val restartBiliBili = task("restartBiliBili").apply {
    doLast {
        exec {
            commandLine(adbExecutable, "shell", "am", "force-stop", "tv.danmaku.bili")
        }
        exec {
            commandLine(
                adbExecutable,
                "shell",
                "am",
                "start",
                "$(pm resolve-activity --components tv.danmaku.bili)"
            )
        }
    }
}

afterEvaluate {
    tasks.getByPath("installDebug").finalizedBy(restartBiliBili)
}
