plugins {
  id("base.convention")
}

allprojects {
  apply(plugin = "base.convention")
}

tasks.register("release") {
  group = "publishing"
  description = "Publish Mosaic to Maven Central and the Gradle Plugin Portal"

  dependsOn("releaseToMavenCentral", ":mosaic-gradle-plugin:publishPlugins")
}

tasks.register("releaseToMavenCentral") {
  group = "publishing"
  description = "Publish Mosaic modules to Maven Central"
  dependsOn(
    ":mosaic-core:publishAndReleaseToMavenCentral",
    ":mosaic-test:publishAndReleaseToMavenCentral",
    ":mosaic-bom:publishAndReleaseToMavenCentral",
    ":mosaic-compiler-plugin:publishAndReleaseToMavenCentral",
    ":mosaic-gradle-plugin:publishAndReleaseToMavenCentral",
  )
}
