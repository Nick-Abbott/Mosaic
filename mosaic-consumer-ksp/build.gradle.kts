description = "KSP processor to register implemented tiles"

plugins {
  id("ksp-processor.convention")
}

dependencies {
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)
  implementation(libs.ksp)
}
