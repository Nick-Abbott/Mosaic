/*
 * Root build.gradle.kts for Mosaic multi-module project
 * Contains common configuration applied to all subprojects
 */

plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  id("com.abbott.mosaic.kotlin") apply false
  id("com.abbott.mosaic.quality") apply false
  id("com.abbott.mosaic.testing") apply false
}

// Configure all subprojects
subprojects {
  apply(plugin = "com.abbott.mosaic.kotlin")
  apply(plugin = "com.abbott.mosaic.quality")
  apply(plugin = "com.abbott.mosaic.testing")

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
