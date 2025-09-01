group = "com.abbott.mosaic"
version = "1.0.0"

plugins {
  id("kotlin.convention")
  id("quality.convention")
}

dependencies {
  implementation(project(":mosaic-core"))
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)
  implementation(libs.ksp)
}
