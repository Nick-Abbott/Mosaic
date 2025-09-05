rootProject.name = "Mosaic"

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

include("mosaic-core")
include("mosaic-test")
include("mosaic-build-plugin")
include("mosaic-build-ksp")
include("mosaic-catalog-ksp")
include("mosaic-bom")
