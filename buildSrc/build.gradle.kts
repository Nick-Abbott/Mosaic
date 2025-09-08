plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation(kotlin("gradle-plugin", libs.versions.kotlin.get()))
  implementation(libs.kotlin.dsl)
  implementation(libs.ktlint.gradle.plugin)
  implementation(libs.detekt.gradle.plugin)
  implementation(libs.kover.gradle.plugin)
  implementation(libs.dokka.gradle.plugin)
  implementation(libs.dokka.javadoc.gradle.plugin)
  implementation(libs.maven.publish)
  implementation(libs.plugin.publish)
}
