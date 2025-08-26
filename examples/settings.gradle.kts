rootProject.name = "mosaic-examples"

pluginManagement {
    includeBuild("../build-logic")
    includeBuild("../packages")
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

// Future examples can be added here:
// include("some-example")
