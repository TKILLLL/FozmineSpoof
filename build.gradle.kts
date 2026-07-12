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

        maven {
            name = "PlaceholderAPI"
            url = uri("https://repo.helpch.at/releases")
        }

        maven {
            name = "PaperMC"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
    }

    dependencies {
        compileOnly("me.clip:placeholderapi:2.12.3")
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
