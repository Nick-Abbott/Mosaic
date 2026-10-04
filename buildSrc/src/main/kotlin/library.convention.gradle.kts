plugins {
  `java-library`
  id("org.jetbrains.dokka")
  id("org.jetbrains.dokka-javadoc")
  id("publish.convention")
}

dokka {
  pluginsConfiguration.html { separateInheritedMembers.set(true) }
  dokkaSourceSets.configureEach {
    sourceLink {
      remoteUrl("https://github.com/BuildMosaic/Mosaic/tree/main/${project.name}/src/main/kotlin")
      remoteLineSuffix.set("#L")
      localDirectory.set(file("src/main/kotlin"))
    }
  }
}
