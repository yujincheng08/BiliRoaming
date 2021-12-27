import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifact
import com.google.protobuf.gradle.*
import java.nio.file.Paths

// https://github.com/google/protobuf-gradle-plugin/issues/540#issuecomment-1001053066
fun com.android.build.api.dsl.AndroidSourceSet.proto(action: SourceDirectorySet.() -> Unit) {
    (this as? ExtensionAware)
        ?.extensions
        ?.getByName("proto")
        ?.let { it as? SourceDirectorySet }
        ?.apply(action)
}


plugins {
    id("com.android.application")
    id("kotlin-android")
    id("com.google.protobuf")
}

val releaseStoreFile: String? by rootProject
val releaseStorePassword: String? by rootProject
val releaseKeyAlias: String? by rootProject
val releaseKeyPassword: String? by rootProject

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
            releaseStoreFile?.also {
                storeFile = rootProject.file(it)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        all {
            signingConfig =
                if (releaseStoreFile.isNullOrEmpty()) signingConfigs.getByName("debug") else signingConfigs.getByName(
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
        freeCompilerArgs = listOf(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
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

abstract class CopyApksTask : DefaultTask() {
    @get:Internal
    abstract val transformer: Property<(input: BuiltArtifact) -> File>

    @get:InputDirectory
    abstract val apkFolder: DirectoryProperty

    @get:OutputDirectory
    abstract val outFolder: DirectoryProperty

    @get:Internal
    abstract val transformationRequest: Property<ArtifactTransformationRequest<CopyApksTask>>

    @TaskAction
    fun taskAction() = transformationRequest.get().submit(this) { builtArtifact ->
        File(builtArtifact.outputFile).copyTo(transformer.get()(builtArtifact), true)
    }
}

androidComponents.onVariants { variant ->
    if (variant.name != "release") return@onVariants
    val updateArtifact = project.tasks.register<CopyApksTask>("copy${variant.name.capitalize()}Apk")
    val transformationRequest = variant.artifacts.use(updateArtifact)
        .wiredWithDirectories(CopyApksTask::apkFolder, CopyApksTask::outFolder)
        .toTransformMany(SingleArtifact.APK)
    updateArtifact.configure {
        this.transformationRequest.set(transformationRequest)
        transformer.set { builtArtifact ->
            File(projectDir, "${variant.name}/BiliRoaming_${builtArtifact.versionName}.apk")
        }
    }
}

configurations.all {
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk7")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    implementation("com.google.protobuf:protobuf-javalite:3.19.1")
    compileOnly("com.google.protobuf:protoc:3.19.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
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
            adbExecutable, "shell", "am", "start",
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
        "optimized_processed_res", "release", "resources-release-optimize.ap_"
    )
    val optimized = File("${zip}.opt")
    val cmd = exec {
        commandLine(aapt2, "optimize", "--collapse-resource-names", "-o", optimized, zip)
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
