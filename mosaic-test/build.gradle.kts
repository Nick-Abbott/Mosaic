plugins {
  id("kotlin.convention")
  id("quality.convention")
  id("testing.convention")
}

dependencies {
  // Core Mosaic dependency
  implementation(project(":mosaic-core"))

  // Coroutines dependency for main source set
  implementation(libs.kotlinx.coroutines.core)

  // Testing dependencies - needed for main source set since this is a testing framework
  implementation(kotlin("test"))
  implementation(libs.kotlinx.coroutines.test)
  implementation(libs.junit.jupiter)
  implementation(libs.junit.jupiter.api)
  implementation(libs.junit.jupiter.engine)

  // Mocking and assertion libraries
  implementation(libs.mockk)
  implementation(libs.assertj)
}
