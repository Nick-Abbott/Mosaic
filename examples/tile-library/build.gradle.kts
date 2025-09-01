plugins {
  kotlin("jvm")
  id("com.google.devtools.ksp")
}

dependencies {
  implementation("com.abbott.mosaic:mosaic-core:1.0.0")
  implementation(libs.kotlinx.coroutines.core)
  ksp("com.abbott.mosaic:mosaic-catalog-ksp:1.0.0")

  testImplementation("com.abbott.mosaic:mosaic-test:1.0.0")
  testImplementation(kotlin("test"))
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
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
