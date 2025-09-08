plugins {
  id("org.buildmosaic.catalog")
  kotlin("plugin.serialization") version "2.2.10"
}

dependencies {
  // KotlinX Serialization
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

  // Coroutines
  implementation(libs.kotlinx.coroutines.core)

  // Test dependencies
  testImplementation(kotlin("test"))
}

kotlin {
  jvmToolchain(21)
}

tasks.withType<Test> {
  useJUnitPlatform()
}
