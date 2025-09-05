plugins {
  kotlin("jvm")
  kotlin("plugin.serialization") version "2.2.10"
  id("com.google.devtools.ksp")
  id("com.buildmosaic.gradle") version "0.1.0"
  id("io.micronaut.application") version "4.5.4"
}

dependencies {
  implementation(project(":tile-library"))

  // Micronaut dependencies
  implementation("io.micronaut:micronaut-http-server-netty")
  implementation("io.micronaut:micronaut-jackson-databind")
  implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation("org.yaml:snakeyaml")
  implementation(libs.kotlinx.coroutines.core)
  implementation("jakarta.inject:jakarta.inject-api:2.0.1")

  // Testing
  testImplementation("io.micronaut.test:micronaut-test-junit5")
  testImplementation("io.micronaut:micronaut-http-client")
  testImplementation(kotlin("test"))
}

kotlin {
  jvmToolchain(21)
}

micronaut {
  version("4.9.3")
  runtime("netty")
  testRuntime("junit5")
  processing {
    incremental(true)
    annotations("com.buildmosaic.micronaut.*")
  }
}

application {
  mainClass.set("com.buildmosaic.micronaut.orders.MicronautExampleApplicationKt")
}
