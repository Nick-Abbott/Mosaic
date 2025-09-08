plugins {
  id("kotlin.convention")
  id("quality.convention")
  id("org.gradle.kotlin.kotlin-dsl")
  `java-gradle-plugin`
  id("publish.convention")
  id("com.gradle.plugin-publish")
}

gradlePlugin {
  website = "https://github.com/Nick-Abbott/Mosaic/${project.name}/"
  vcsUrl  = "https://github.com/Nick-Abbott/Mosaic/${project.name}/"

  plugins.all {
    tags.set(
      listOf(
        "mosaic",
        "buildmosaic",
        "tile",
        "gradle",
        "plugin",
        "kotlin",
      )
    )
  }
}
