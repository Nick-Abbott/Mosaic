rootProject.name = "Mosaic"

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

include("mosaic-core")
include("mosaic-test")
include("mosaic-bom")
include("mosaic-analysis-core")
include("mosaic-compiler-plugin")
include("mosaic-gradle-plugin")
include("mosaic-benchmarks")
