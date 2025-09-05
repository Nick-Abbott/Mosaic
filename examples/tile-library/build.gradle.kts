plugins {
  kotlin("jvm")
  kotlin("plugin.serialization") version "2.2.10"
  id("com.google.devtools.ksp")
}

dependencies {
  // BOM File
  platform("com.buildmosaic:mosaic-bom:0.1.0")

  // Mosaic Core
  implementation("com.buildmosaic:mosaic-core")

  // KotlinX Serialization
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

  // Mosaic Catalog KSP
  ksp("com.buildmosaic:mosaic-catalog-ksp")

  // Coroutines
  implementation(libs.kotlinx.coroutines.core)

  // Mosaic Test
  testImplementation("com.buildmosaic:mosaic-test")
  testImplementation(kotlin("test"))
}

kotlin {
  jvmToolchain(21)
}

tasks.withType<Test> {
  useJUnitPlatform()
}
