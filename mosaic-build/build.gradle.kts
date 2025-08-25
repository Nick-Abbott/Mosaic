/*
 * Mosaic Build module build.gradle.kts
 * This module provides a Gradle plugin for automatic Mosaic tile registration
 */

plugins {
  `java-gradle-plugin`
}

dependencies {
  implementation(project(":packages:mosaic-core"))
  implementation(libs.kotlinpoet)
  implementation(libs.classgraph)
}

gradlePlugin {
  plugins {
    create("mosaicBuild") {
      id = "com.abbott.mosaic.build"
      implementationClass = "com.abbott.mosaic.build.MosaicBuildPlugin"
    }
  }
}
