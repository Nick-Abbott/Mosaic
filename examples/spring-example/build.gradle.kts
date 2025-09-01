plugins {
  kotlin("jvm")
  id("com.google.devtools.ksp")
  id("com.abbott.mosaic.build")
  id("org.jetbrains.kotlin.plugin.spring") version "2.2.10"
  application
}

dependencies {
  implementation("com.abbott.mosaic:mosaic-core:1.0.0")
  implementation(project(":tile-library"))
  implementation("org.springframework.boot:spring-boot-starter-web:3.2.5")
  implementation(libs.kotlinx.coroutines.core)
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

application {
  mainClass.set("com.abbott.mosaic.examples.spring.orders.SpringExampleApplicationKt")
}
