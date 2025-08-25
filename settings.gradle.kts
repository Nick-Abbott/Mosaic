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

include("packages:mosaic-core")
include("packages:mosaic-test")
include("mosaic-build")

// Future modules can be added here:
// include("mosaic-api")
// include("mosaic-web")
// include("mosaic-cli")
