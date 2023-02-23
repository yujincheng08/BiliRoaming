import com.google.protobuf.gradle.*

@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
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
        if (it.buildType == "release") {
            file("${it.name}/BiliRoaming_${appVerName}.apk")
        } else {
            null
        }
    }
}

cmaker {
    default {
        targets("biliroaming")
        abiFilters("armeabi-v7a", "arm64-v8a", "x86")
        arguments += "-DANDROID_STL=none"
        cppFlags += "-Wno-c++2b-extensions"
    }

    buildTypes {
        arguments += "-DDEBUG_SYMBOLS_PATH=${project.buildDir.absolutePath}/symbols/${it.name}"
    }
}

android {
    compileSdk = 33
    buildToolsVersion = "33.0.2"
    ndkVersion = "25.2.9519653"

    defaultConfig {
        applicationId = "me.iacn.biliroaming"
        minSdk = 24
        targetSdk = 33  // Target Android T
        versionCode = appVerCode
        versionName = appVerName
    }

    buildFeatures {
        prefab = true
    }

    androidResources {
        noCompress("libbiliroaming.so")
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
            "-Xuse-k2",
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
            "-opt-in=kotlin.RequiresOptIn",
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

    packagingOptions {
        resources {
            excludes += arrayOf("**")
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
            version = "3.22.1+"
        }
    }
    namespace = "me.iacn.biliroaming"
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }

    generatedFilesBaseDir = "$projectDir/src/generated"

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

val restartBiliBili = task("restartBiliBili").doLast {
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

afterEvaluate {
    tasks.getByPath("installDebug").finalizedBy(restartBiliBili)
}
