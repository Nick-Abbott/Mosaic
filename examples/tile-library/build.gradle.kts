plugins {
  kotlin("jvm")
  kotlin("plugin.serialization") version "2.2.10"
  id("com.google.devtools.ksp")
}

dependencies {
  // BOM File
  platform("org.buildmosaic:mosaic-bom:0.1.0")

  // Mosaic Core
  implementation("org.buildmosaic:mosaic-core")

  // KotlinX Serialization
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

  // Mosaic Catalog KSP
  ksp("org.buildmosaic:mosaic-catalog-ksp")

  // Coroutines
  implementation(libs.kotlinx.coroutines.core)

  // Mosaic Test
  testImplementation("org.buildmosaic:mosaic-test")
  testImplementation(kotlin("test"))
}

kotlin {
  jvmToolchain(21)
}

tasks.withType<Test> {
  useJUnitPlatform()
}
