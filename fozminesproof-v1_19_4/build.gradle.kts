plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.7.5"
}

dependencies {
    implementation(project(":fozminesproof-api"))

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
