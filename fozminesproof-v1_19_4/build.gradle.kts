plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

dependencies {
    implementation(project(":fozminespoof-api"))

    paperweight.paperDevBundle("1.19.4-R0.1-SNAPSHOT")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks {
    assemble {
        dependsOn(reobfJar)
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
}
