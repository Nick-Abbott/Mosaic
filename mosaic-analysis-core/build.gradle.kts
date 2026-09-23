description = "Compiler-independent semantic analysis for Mosaic contracts"

plugins {
  id("kotlin.convention")
  id("quality.convention")
  id("testing.convention")
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  implementation(libs.kotlinx.serialization.json)
}
