plugins {
  kotlin("jvm")
  kotlin("plugin.serialization") version "2.2.10"
  id("com.google.devtools.ksp")
}

dependencies {
  implementation("com.abbott.mosaic:mosaic-core:1.0.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  ksp("com.abbott.mosaic:mosaic-catalog-ksp:1.0.0")
  implementation(libs.kotlinx.coroutines.core)
  testImplementation("com.abbott.mosaic:mosaic-test:1.0.0")
  testImplementation(kotlin("test"))
  testImplementation(libs.junit.jupiter)
}

kotlin {
  jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}
