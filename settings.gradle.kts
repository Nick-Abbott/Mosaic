rootProject.name = "Mosaic"

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

include("mosaic-core")
include("mosaic-test")
include("mosaic-consumer-plugin")
include("mosaic-consumer-ksp")
include("mosaic-catalog-plugin")
include("mosaic-catalog-ksp")
include("mosaic-bom")
