rootProject.name = "examples"

dependencyResolutionManagement {
  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }
}

pluginManagement {
  includeBuild("..")
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

include("spring-example")
include("ktor-example")
include("micronaut-example")
include("tile-library")
includeBuild("..")
