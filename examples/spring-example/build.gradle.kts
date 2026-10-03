plugins {
  kotlin("jvm")
  id("org.jetbrains.kotlin.plugin.spring") version "2.4.20"
  application
}

val mosaicVersion: String by rootProject.extra

dependencies {
  implementation(project(":tile-library"))
  implementation("org.buildmosaic:mosaic-core:$mosaicVersion")
  implementation("org.springframework.boot:spring-boot-starter-web:3.2.5")
  implementation(libs.kotlinx.coroutines.core)
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

application {
  mainClass.set("org.buildmosaic.spring.orders.SpringExampleApplicationKt")
}
