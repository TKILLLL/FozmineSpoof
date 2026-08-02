import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.4.2"
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.6.1")
    }
}

repositories {
    mavenCentral()
    maven("https://papermc.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")

    implementation(project(":fozminespoof-api"))

    implementation(project(path = ":fozminespoof-v1_19_4", configuration = "reobf"))
    implementation(project(path = ":fozminespoof-v1_20_1", configuration = "reobf"))
    implementation(project(path = ":fozminespoof-v1_20_2", configuration = "reobf"))
    implementation(project(path = ":fozminespoof-v1_20_4", configuration = "reobf"))
    implementation(project(path = ":fozminespoof-v1_20_6", configuration = "reobf"))
    implementation(project(path = ":fozminespoof-v1_21_1", configuration = "reobf"))
    implementation(project(path = ":fozminespoof-v1_21_4", configuration = "reobf"))
    implementation(project(path = ":fozminespoof-v1_21_11", configuration = "reobf"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

// ĐÃ FIX: Khai báo Service lấy JDK 17 ngoài phạm vi Task
val javaToolchains = project.extensions.getByType<JavaToolchainService>()
val java17Launcher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(17))
}

tasks {
    runServer {
        minecraftVersion("1.19.4")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)

        filesMatching("plugin.yml") {
            filter<ReplaceTokens>("tokens" to props)
        }
    }

    shadowJar {
        archiveClassifier.set("raw")
        configurations.set(listOf(project.configurations.runtimeClasspath.get()))
        mergeServiceFiles()
    }

    register<proguard.gradle.ProGuardTask>("obfuscate") {
        dependsOn(shadowJar)

        injars(shadowJar.get().archiveFile)
        outjars(layout.buildDirectory.file("libs/${project.name}-${project.version}.jar"))

        configuration("proguard-rules.pro")

        val jdkHome = java17Launcher.get().metadata.installationPath.asFile.absolutePath
        libraryjars("$jdkHome/jmods")

        libraryjars(configurations.compileClasspath.get())
    }

    build {
        dependsOn("obfuscate")
    }
}