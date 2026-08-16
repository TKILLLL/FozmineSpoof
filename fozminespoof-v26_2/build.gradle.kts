plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

dependencies {
    compileOnly(project(":fozminespoof-api"))

    paperweight.paperDevBundle("26.2.build.+")
}

paperweight {
    reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks {
    reobfJar {
        enabled = false
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
}