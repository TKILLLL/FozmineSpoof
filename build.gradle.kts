plugins {
    java
    id("com.gradleup.shadow") version "9.4.2" apply false
    id("xyz.jpenilla.run-paper") version "3.0.2" apply false
    id("io.papermc.paperweight.userdev") version "1.7.5" apply false
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()

        maven("https://repo.papermc.io/repository/maven-public/")
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
