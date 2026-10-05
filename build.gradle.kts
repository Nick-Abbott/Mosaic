plugins {
  id("base.convention")
  id("dokka.convention")
  id("org.jetbrains.dokka-javadoc")
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
    ":mosaic-opentelemetry:publishAndReleaseToMavenCentral",
    ":mosaic-bom:publishAndReleaseToMavenCentral",
    ":mosaic-compiler-plugin:publishAndReleaseToMavenCentral",
    ":mosaic-gradle-plugin:publishAndReleaseToMavenCentral",
  )
}

dependencies {
  dokka(project(":mosaic-core"))
  dokka(project(":mosaic-test"))
  dokka(project(":mosaic-opentelemetry"))
}

dokka {
  moduleName.set("Mosaic")
}
