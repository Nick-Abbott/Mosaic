group = "com.buildmosaic.build.ksp"
version = "1.0.0"

plugins {
  id("kotlin.convention")
  id("quality.convention")
}

dependencies {
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)
  implementation(libs.ksp)
}
