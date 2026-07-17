pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
        mavenCentral()
    }
}

rootProject.name = "FozmineSproof"

include("fozminesproof-api")
include("fozminesproof-core")
include("fozminesproof-v1_19_4")
include("fozminesproof-v1_20_2")
include("fozminesproof-v1_20_4")
include("fozminesproof-v1_21_4")
include("fozminesproof-v1_21_11")
