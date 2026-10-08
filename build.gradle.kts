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

// Deliberately disconnected from build/check/test and all validation workflows.
listOf(
  "certifyKotlin" to "all",
  "certifyKotlinRuntime" to "runtime",
  "certifyKotlinAnalysis" to "analysis",
).forEach { (taskName, scope) ->
  tasks.register<Exec>(taskName) {
    group = "compatibility"
    description = "On-demand $scope certification of an exact -Pcompat.kotlin version"
    commandLine(
      "python3",
      layout.projectDirectory.file("compatibility/certify.py").asFile.absolutePath,
      "--kotlin",
      providers.gradleProperty("compat.kotlin").getOrElse(""),
      "--scope",
      scope,
      "--java-installations",
      providers.gradleProperty("org.gradle.java.installations.paths").getOrElse(""),
    )
    // Certification always produces fresh evidence, including failures.
    outputs.upToDateWhen { false }
  }
}
