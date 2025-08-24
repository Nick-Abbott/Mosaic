rootProject.name = "Mosaic"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("mosaic-core")
include("mosaic-test")

// Future modules can be added here:
// include("mosaic-api")
// include("mosaic-web")
// include("mosaic-cli")
