description = "KSP processor to build Mosaic tile catalogs for tile libraries"

plugins {
  id("ksp-processor.convention")
}

dependencies {
  implementation(project(":mosaic-core"))
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)
  implementation(libs.ksp)
}
