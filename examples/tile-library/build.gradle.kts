plugins {
  kotlin("jvm")
  kotlin("plugin.serialization") version "2.2.10"
}

val mosaicVersion: String by rootProject.extra

dependencies {
  // KotlinX Serialization
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation("org.buildmosaic:mosaic-core:$mosaicVersion")

  // Coroutines
  implementation(libs.kotlinx.coroutines.core)

  // Test dependencies
  testImplementation(kotlin("test"))
  testImplementation("org.buildmosaic:mosaic-test:$mosaicVersion")
  testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
  jvmToolchain(21)
}

tasks.withType<Test> {
  useJUnitPlatform()
}
