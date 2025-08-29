plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation(kotlin("gradle-plugin", libs.versions.kotlin.get()))
  implementation(libs.ktlint.gradle.plugin)
  implementation(libs.detekt.gradle.plugin)
  implementation(libs.kover.gradle.plugin)
}
