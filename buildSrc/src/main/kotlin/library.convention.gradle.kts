plugins {
  `java-library`
  id("org.jetbrains.dokka")
  id("org.jetbrains.dokka-javadoc")
  id("publish.convention")
}

dokka {
  dokkaSourceSets.configureEach {
    sourceLink {
      remoteUrl("https://github.com/Nick-Abbott/Mosaic/tree/main/")
      localDirectory.set(file("src/main/kotlin"))
    }
  }
}
