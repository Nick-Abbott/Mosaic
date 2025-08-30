plugins {
  id("kotlin.convention")
  id("quality.convention")
  id("testing.convention")
}

dependencies {
  implementation(project(":mosaic-core"))
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)
  implementation(libs.ksp)

  testImplementation(libs.mockk)
}
