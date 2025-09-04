plugins {
  kotlin("jvm")
  kotlin("plugin.serialization") version "2.2.10"
  id("com.google.devtools.ksp")
  id("com.buildmosaic.gradle")
  application
}

dependencies {
  implementation("com.buildmosaic.core:mosaic-core:1.0.0")
  implementation(project(":tile-library"))
  implementation("io.ktor:ktor-server-core:2.3.12")
  implementation("io.ktor:ktor-server-netty:2.3.12")
  implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
  implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
  implementation("io.ktor:ktor-server-status-pages:2.3.12")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation(libs.kotlinx.coroutines.core)
  testImplementation("com.buildmosaic.test:mosaic-test:1.0.0")
  testImplementation("io.ktor:ktor-server-tests:2.3.12")
  testImplementation("io.ktor:ktor-client-content-negotiation:2.3.12")
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

application {
  mainClass.set("com.buildmosaic.ktor.orders.KtorExampleApplicationKt")
}
