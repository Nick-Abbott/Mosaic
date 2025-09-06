plugins {
  kotlin("jvm")
  id("com.google.devtools.ksp")
  id("org.buildmosaic.gradle") version "0.1.0"
  id("org.jetbrains.kotlin.plugin.spring") version "2.2.10"
  application
}

dependencies {
  implementation(project(":tile-library"))
  implementation("org.springframework.boot:spring-boot-starter-web:3.2.5")
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(kotlin("test"))
}

kotlin {
  jvmToolchain(21)
}

tasks.withType<Test> {
  useJUnitPlatform()
}

application {
  mainClass.set("org.buildmosaic.spring.orders.SpringExampleApplicationKt")
}
