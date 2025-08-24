/*
 * Mosaic Test module build.gradle.kts
 * This module contains testing utilities and framework for testing Tile implementations
 */

dependencies {
  // Core Mosaic dependency
  implementation(project(":mosaic-core"))

  // Coroutines dependency for main source set
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

  // Testing dependencies - needed for main source set since this is a testing framework
  implementation(kotlin("test"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
  implementation("org.junit.jupiter:junit-jupiter:5.10.0")
  implementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
  implementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")

  // Mocking and assertion libraries
  implementation("io.mockk:mockk:1.13.8")
  implementation("org.assertj:assertj-core:3.24.2")
}

kotlin {
  jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
  }
}

// kover configuration - reasonable thresholds based on achieved coverage
koverReport {
  filters {
    // Include all classes for comprehensive coverage analysis
  }

  verify {
    rule {
      isEnabled = true
      entity = kotlinx.kover.gradle.plugin.dsl.GroupingEntityType.APPLICATION

      bound {
        minValue = 90
        metric = kotlinx.kover.gradle.plugin.dsl.MetricType.LINE
        aggregation = kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
      }

      bound {
        minValue = 80
        metric = kotlinx.kover.gradle.plugin.dsl.MetricType.BRANCH
        aggregation = kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
      }

      bound {
        minValue = 90
        metric = kotlinx.kover.gradle.plugin.dsl.MetricType.INSTRUCTION
        aggregation = kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
      }
    }
  }
}
