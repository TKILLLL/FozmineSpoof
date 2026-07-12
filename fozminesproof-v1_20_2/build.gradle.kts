plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.7.5"
}

dependencies {
    implementation(project(":fozminesproof-api"))

    paperweight.paperDevBundle("1.20.2-R0.1-SNAPSHOT")
}

tasks {
    assemble {
        dependsOn("reobfJar")
    }

    compileJava {
        options.encoding = "UTF-8"
    }
}
