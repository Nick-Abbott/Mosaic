plugins {
  id("base.convention")
}

allprojects {
  apply(plugin = "base.convention")
}

tasks.register("release") {
  group = "publishing"
  description = "Publish all publications to Maven Central and publish plugins to the Gradle Plugin Portal"
  
  subprojects.forEach { subproject ->
    if (subproject.plugins.hasPlugin("maven-publish")) {
      dependsOn("${subproject.name}:publishToMavenCentral")
    }
    if (subproject.plugins.hasPlugin("com.gradle.plugin-publish")) {
      dependsOn("${subproject.name}:publishPlugins")
    }
  }
}
  
