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
  subprojects.forEach { subproject ->
    if (subproject.plugins.hasPlugin("maven-publish")) {
      dependsOn("${subproject.name}:publishToMavenCentral")
    }
  }
}
