group = "org.buildmosaic"
version = project.property("mosaic.version") as String

plugins {
  id("kotlin.convention")
  id("quality.convention")
}

dependencies {
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)
  implementation(libs.ksp)
}
