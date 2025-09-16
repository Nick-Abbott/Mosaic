plugins {
  kotlin("jvm")
  kotlin("plugin.serialization") version "2.2.10"
}

dependencies {
  // KotlinX Serialization
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation("org.buildmosaic:mosaic-core:0.1.0")

  // Coroutines
  implementation(libs.kotlinx.coroutines.core)

  // Test dependencies
  testImplementation(kotlin("test"))
  testImplementation("org.buildmosaic:mosaic-test:0.1.0")
  testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
  jvmToolchain(21)
}

tasks.withType<Test> {
  useJUnitPlatform()
}
