rootProject.name = "Mosaic"

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

include("mosaic-core")
include("mosaic-test")
include("mosaic-build")
include("mosaic-metadata")
include("examples:spring-example")
