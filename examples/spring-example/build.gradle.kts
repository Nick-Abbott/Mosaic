plugins {
  alias(libs.plugins.ksp)
  id("org.jetbrains.kotlin.plugin.spring") version "2.2.10"
  application
}

dependencies {
  implementation(project(":mosaic-core"))
  implementation("org.springframework.boot:spring-boot-starter-web:3.2.5")
  implementation(libs.kotlinx.coroutines.core)
  ksp(project(":mosaic-build"))
  testImplementation(project(":mosaic-test"))
}

application {
  mainClass.set("com.abbott.mosaic.examples.spring.orders.SpringExampleApplicationKt")
}
