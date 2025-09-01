plugins {
  kotlin("jvm")
  kotlin("plugin.serialization") version "2.2.10"
  id("com.google.devtools.ksp")
  id("com.abbott.mosaic.build")
  id("io.micronaut.application") version "4.4.2"
}

dependencies {
  implementation("com.abbott.mosaic:mosaic-core:1.0.0")
  implementation(project(":tile-library"))
  
  // Micronaut dependencies
  implementation("io.micronaut:micronaut-http-server-netty")
  implementation("io.micronaut:micronaut-jackson-databind")
  implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation(libs.kotlinx.coroutines.core)
  
  // Testing
  testImplementation("com.abbott.mosaic:mosaic-test:1.0.0")
  testImplementation("io.micronaut.test:micronaut-test-junit5")
  testImplementation("io.micronaut:micronaut-http-client")
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

micronaut {
  runtime("netty")
  testRuntime("junit5")
  processing {
    incremental(true)
    annotations("com.abbott.mosaic.examples.micronaut.*")
  }
}

application {
  mainClass.set("com.abbott.mosaic.examples.micronaut.orders.MicronautExampleApplicationKt")
}
