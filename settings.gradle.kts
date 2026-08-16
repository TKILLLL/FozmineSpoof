pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
        mavenCentral()
    }
}

rootProject.name = "FozmineSpoof"

include("fozminespoof-api")
include("fozminespoof-core")
include("fozminespoof-v1_19_4")
include("fozminespoof-v1_20_1")
include("fozminespoof-v1_20_2")
include("fozminespoof-v1_20_4")
include("fozminespoof-v1_20_6")
include("fozminespoof-v1_21_1")
include("fozminespoof-v1_21_4")
include("fozminespoof-v1_21_11")
include("fozminespoof-v26_1_1")
include("fozminespoof-v26_2")
