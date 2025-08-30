plugins {
  id("kotlin.convention")
  id("quality.convention")
}

dependencies {
  implementation(project(":mosaic-core"))
  implementation(libs.ksp)
}
