plugins {
  kotlin("jvm")
  kotlin("plugin.serialization") version "2.2.10"
  application
}

dependencies {
  implementation(project(":tile-library"))
  implementation("org.buildmosaic:mosaic-core:0.2.0")
  implementation("io.ktor:ktor-server-core:2.3.12")
  implementation("io.ktor:ktor-server-netty:2.3.12")
  implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
  implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
  implementation("io.ktor:ktor-server-status-pages:2.3.12")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation(libs.kotlinx.coroutines.core)
  testImplementation("io.ktor:ktor-server-tests:2.3.12")
  testImplementation("io.ktor:ktor-client-content-negotiation:2.3.12")
  testImplementation(kotlin("test"))
  testImplementation("org.buildmosaic:mosaic-test:0.2.0")
  testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
  jvmToolchain(21)
}

tasks.withType<Test> {
  useJUnitPlatform()
}

application {
  mainClass.set("org.buildmosaic.ktor.orders.KtorExampleApplicationKt")
}
