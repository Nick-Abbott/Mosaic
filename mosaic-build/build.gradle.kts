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
