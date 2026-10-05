plugins {
  `java-library`
  id("dokka.convention")
  id("org.jetbrains.dokka-javadoc")
  id("publish.convention")
}

dokka {
  dokkaSourceSets.configureEach {
    sourceLink {
      remoteUrl("https://github.com/BuildMosaic/Mosaic/tree/main/${project.name}/src/main/kotlin")
      remoteLineSuffix.set("#L")
      localDirectory.set(file("src/main/kotlin"))
    }
  }
}
