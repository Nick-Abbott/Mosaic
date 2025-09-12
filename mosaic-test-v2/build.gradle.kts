description = "A testing framework for tile isolation in Mosaic v2"

plugins {
  id("kotlin.convention")
  id("quality.convention")
  id("testing.convention")
  id("library.convention")
}

tasks.withType<Test> {
  jvmArgs =
    listOf(
      "-XX:+EnableDynamicAgentLoading",
      "-Djdk.instrument.traceUsage=false",
    )
}

dependencies {
  // Core Mosaic v2 dependency
  implementation(project(":mosaic-core-v2"))

  // Coroutines dependency for main source set
  implementation(libs.kotlinx.coroutines.core)

  // Testing dependencies - needed for main source set since this is a testing framework
  implementation(kotlin("test"))
  implementation(libs.kotlinx.coroutines.test)

  // Mocking and assertion libraries
  implementation(libs.mockk)
}
