import com.google.protobuf.gradle.*
import com.android.build.api.artifact.SingleArtifact
import java.nio.file.Paths

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("com.google.protobuf")
}

val androidStoreFile: String? by rootProject
val androidStorePassword: String? by rootProject
val androidKeyAlias: String? by rootProject
val androidKeyPassword: String? by rootProject
val appVerCode: String by rootProject
val appVerName: String by rootProject

android {
    compileSdk = 32
    buildToolsVersion = "32.0.0"

    defaultConfig {
        applicationId = "me.iacn.biliroaming"
        minSdk = 21
        targetSdk = 32  // Target Android Sv2
        versionCode = appVerCode.toInt()
        versionName = appVerName
    }

    signingConfigs {
        create("config") {
            androidStoreFile?.also {
                storeFile = rootProject.file(it)
                storePassword = androidStorePassword
                keyAlias = androidKeyAlias
                keyPassword = androidKeyPassword
            }
        }
    }

    buildTypes {
        all {
            signingConfig =
                if (androidStoreFile.isNullOrEmpty()) signingConfigs.getByName("debug") else signingConfigs.getByName(
                    "config"
                )
        }
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
    }

//    applicationVariants.all { variant ->
//        variant.outputs.all { output ->
//            if (variant.buildType.name == 'release') {
//                outputFileName = "BiliRoaming_${defaultConfig.versionName}.apk"
//            }
//        }
//    }

    sourceSets {
        getByName("main").proto {
            proto {
                srcDir("src/main/proto")
                include("**/*.proto")
            }
        }
    }

    packagingOptions {
        resources {
            excludes += arrayOf("META-INF/**", "kotlin/**", "google/**", "**.bin")
        }
    }


    lint {
        isCheckReleaseBuilds = false
    }

    dependenciesInfo {
        includeInApk = false
    }
    androidResources {
        additionalParameters += arrayOf("--allow-reserved-package-id", "--package-id", "0x23")
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.19.1"
    }

    generatedFilesBaseDir = "$projectDir/src/generated"

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                id("java") {
                    option("lite")
                }
            }
        }
    }
}
val copyApk = project.tasks.register<Copy>("copyApk") {
}

androidComponents.onVariants { variant ->
    variant.artifacts.use(copyApk).wiredWith(Copy::from)
}


dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    compileOnly("de.robv.android.xposed:api:82:sources")
    implementation("com.google.protobuf:protobuf-javalite:3.19.1")
    compileOnly("com.google.protobuf:protoc:3.19.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
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
            "--user",
            "0",
            "$(pm resolve-activity --components tv.danmaku.bili)"
        )
    }
}

val optimizeReleaseRes = task("optimizeReleaseRes").doLast {
    val aapt2 = Paths.get(
        project.android.sdkDirectory.path,
        "build-tools", project.android.buildToolsVersion, "aapt2"
    )
    val zip = Paths.get(
        project.buildDir.path, "intermediates",
        "shrunk_processed_res", "release", "resources-release-stripped.ap_"
    )
    val optimized = File("${zip}.opt")
    val cmd = exec {
        commandLine(
            aapt2, "optimize", "--collapse-resource-names",
            "--shorten-resource-paths", "-o", optimized, zip
        )
        isIgnoreExitValue = true
    }
    if (cmd.exitValue == 0) {
        delete(zip)
        optimized.renameTo(zip.toFile())
    }
}
tasks.whenTaskAdded {
    when (name) {
        "optimizeReleaseResources" -> {
            finalizedBy(optimizeReleaseRes)
        }
        "installDebug" -> {
            finalizedBy(restartBiliBili)
        }
    }
}
