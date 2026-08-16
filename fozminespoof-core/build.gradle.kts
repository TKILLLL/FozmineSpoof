import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.4.2"
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
    implementation(project(path = ":fozminespoof-v26_1_1", configuration = "reobf"))
    implementation(project(path = ":fozminespoof-v26_2", configuration = "reobf"))

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
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
        archiveClassifier.set("")
        configurations.set(listOf(project.configurations.runtimeClasspath.get()))
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }
}