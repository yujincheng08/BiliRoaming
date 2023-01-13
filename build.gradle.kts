// Top-level build file where you can add configuration options common to all sub-projects/modules.
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository

buildscript {
    val kotlinVersion by extra("1.7.21")
    val protobufVersion by extra("3.21.12")
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("com.google.protobuf:protobuf-gradle-plugin:0.9.1")
        classpath("org.eclipse.jgit:org.eclipse.jgit:6.3.0.202209071007-r")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

val repo = FileRepository(rootProject.file(".git"))
val refId = repo.refDatabase.exactRef("refs/remotes/origin/master").objectId!!
val commitCount = Git(repo).log().add(refId).call().count()
val appVerCode by extra(commitCount)

allprojects {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://api.xposed.info")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
