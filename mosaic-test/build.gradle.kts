group = "com.buildmosaic.test"
version = "1.0.0"

plugins {
  id("kotlin.convention")
  id("quality.convention")
  id("testing.convention")
}

tasks.withType<Test> {
  jvmArgs =
    listOf(
      "-XX:+EnableDynamicAgentLoading",
      "-Djdk.instrument.traceUsage=false",
    )
}

dependencies {
  // Core Mosaic dependency
  implementation(project(":mosaic-core"))

  // Coroutines dependency for main source set
  implementation(libs.kotlinx.coroutines.core)

  // Testing dependencies - needed for main source set since this is a testing framework
  implementation(kotlin("test"))
  implementation(libs.kotlinx.coroutines.test)

  // Mocking and assertion libraries
  implementation(libs.mockk)
  implementation(libs.assertj)
}
