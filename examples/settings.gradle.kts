rootProject.name = "mosaic-examples"

pluginManagement {
    includeBuild("../build-logic")
    includeBuild("../packages")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

includeBuild("../packages")

dependencyResolutionManagement {
  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }
  repositories {
    mavenCentral()
  }
}

include("spring-example")
