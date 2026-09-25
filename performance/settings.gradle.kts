rootProject.name = "performance"

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }
}

include("shared", "direct-app", "mosaic-app", "comparison-tests")
includeBuild("..")
