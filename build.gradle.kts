plugins {
    java
    id("com.gradleup.shadow") version "9.4.2" apply false
    id("xyz.jpenilla.run-paper") version "3.0.2" apply false
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        maven {
            name = "PaperMC"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
    }

    dependencies {
        implementation("com.zaxxer:HikariCP:7.1.0")
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}