group = "com.buildmosaic"
version = project.property("mosaic.version") as String

plugins {
  id("kotlin.convention")
  id("quality.convention")
  `kotlin-dsl`
  `java-gradle-plugin`
}

// Create a properties file with the version
tasks.processResources {
  val pluginVersion = project.property("mosaic.version") as String
  filesMatching("mosaic-plugin.properties") {
    expand("version" to pluginVersion)
  }
}

dependencies {
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)
  implementation(libs.ksp)

  compileOnly(gradleApi())
  compileOnly(libs.kotlin.gradle.plugin)
  compileOnly(libs.ksp.gradle.plugin)
}

gradlePlugin {
  plugins {
    create("mosaicConsumer") {
      id = "com.buildmosaic.gradle"
      implementationClass = "com.buildmosaic.gradle.plugin.MosaicConsumerPlugin"
      displayName = "Mosaic Consumer Plugin"
      description = "Merges META-INF/mosaic catalogs + wires KSP aggregator"
    }
  }
}
