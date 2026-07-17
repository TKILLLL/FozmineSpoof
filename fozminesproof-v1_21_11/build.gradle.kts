plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

dependencies {
    compileOnly(project(":fozminesproof-api"))

    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
}

tasks {
    assemble {
        dependsOn("reobfJar")
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
}
