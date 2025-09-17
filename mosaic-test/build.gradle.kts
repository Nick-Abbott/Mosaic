description = "A testing framework for tile isolation in Mosaic"

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
  // Core Mosaic dependency
  implementation(project(":mosaic-core"))

  // Coroutines dependency for main source set
  implementation(libs.kotlinx.coroutines.core)

  // Testing dependencies - needed for main source set since this is a testing framework
  implementation(kotlin("test"))
  implementation(libs.kotlinx.coroutines.test)
}
