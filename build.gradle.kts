/*
 * Root build.gradle.kts for Mosaic multi-module project
 * Contains common configuration applied to all subprojects
 */

plugins {
  kotlin("jvm") version "2.2.10" apply false
  id("org.jlleitschuh.gradle.ktlint") version "12.1.0" apply false
  id("io.gitlab.arturbosch.detekt") version "1.23.5" apply false
  id("org.jetbrains.kotlinx.kover") version "0.7.5" apply false
}

// Configure all projects
allprojects {
  group = "com.abbott.mosaic"
  version = "1.0.0-SNAPSHOT"

  repositories {
    mavenCentral()
  }
}

// Configure all subprojects
subprojects {
  apply(plugin = "org.jetbrains.kotlin.jvm")
  apply(plugin = "org.jlleitschuh.gradle.ktlint")
  apply(plugin = "io.gitlab.arturbosch.detekt")
  apply(plugin = "org.jetbrains.kotlinx.kover")

  tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy("koverHtmlReport")
  }

  tasks.register("styleCheck") {
    group = "verification"
    description = "Run all code style and quality checks"
    dependsOn("ktlintCheck", "detekt")
  }

  tasks.register("coverageCheck") {
    group = "verification"
    description = "Run tests and verify coverage thresholds"
    dependsOn("test", "koverVerify")
  }

  tasks.register("verifyAll") {
    group = "verification"
    description = "Run all code style, quality checks, and coverage verification"
    dependsOn("styleCheck", "coverageCheck")
  }

  tasks.named("check") {
    dependsOn("verifyAll")
  }
}

// Root-level convenience tasks
tasks.register("fullBuild") {
  group = "build"
  description = "Clean, build, test, and verify everything across all modules"
  dependsOn(subprojects.map { it.tasks.named("build") })
}

tasks.register("generateReports") {
  group = "reporting"
  description = "Generate all reports (tests, coverage, style checks) across all modules"
  dependsOn(subprojects.map { it.tasks.named("build") })
}

tasks.register("fixCodeStyle") {
  group = "verification"
  description = "Auto-fix code style issues where possible across all modules"
  dependsOn(subprojects.map { it.tasks.named("ktlintFormat") })
}
