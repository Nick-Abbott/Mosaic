plugins {
  id("base.convention")
}

allprojects {
  apply(plugin = "base.convention")
}

tasks.register("release") {
  group = "publishing"
  description = "Publish all Mosaic publications to Maven Central"

  dependsOn("releaseToMavenCentral")
}

tasks.register("releaseToMavenCentral") {
  group = "publishing"
  description = "Publish all Mosaic publications to Maven Central"
  dependsOn(
    ":mosaic-core:publishToMavenCentral",
    ":mosaic-test:publishToMavenCentral",
    ":mosaic-bom:publishToMavenCentral",
    ":mosaic-analysis-core:publishToMavenCentral",
    ":mosaic-compiler-plugin:publishToMavenCentral",
    ":mosaic-gradle-plugin:publishToMavenCentral",
  )
}
