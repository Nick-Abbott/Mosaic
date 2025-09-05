group = "com.buildmosaic.gradle"
version = "1.0.0"

plugins {
  id("kotlin.convention")
  id("quality.convention")
  `kotlin-dsl`
  `java-gradle-plugin`
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
