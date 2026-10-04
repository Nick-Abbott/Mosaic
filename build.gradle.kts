plugins {
  id("base.convention")
  id("org.jetbrains.dokka")
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
  pluginsConfiguration.html {
    customStyleSheets.from(layout.projectDirectory.file("website/api/mosaic.css"))
    customAssets.from(
      layout.projectDirectory.file("brand/mosaic-mark.svg"),
      layout.projectDirectory.file("website/api/mosaic-api.js"),
    )
    separateInheritedMembers.set(true)
    footerMessage.set("<a href=\"https://BuildMosaic.org/\">BuildMosaic.org</a> · <a href=\"https://BuildMosaic.org/start/overview/\">User documentation</a>")
  }
}
