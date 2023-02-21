// Top-level build file where you can add configuration options common to all sub-projects/modules.
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.eclipse.jgit:org.eclipse.jgit:6.3.0.202209071007-r")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

val repo = FileRepository(rootProject.file(".git"))
val refId = repo.refDatabase.exactRef("refs/remotes/origin/master").objectId!!
val commitCount = Git(repo).log().add(refId).call().count()
val appVerCode by extra(commitCount)

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
