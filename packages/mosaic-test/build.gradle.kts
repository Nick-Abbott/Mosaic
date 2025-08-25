/*
 * Mosaic Test module build.gradle.kts
 * This module contains testing utilities and framework for testing Tile implementations
 */

import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

dependencies {
  // Core Mosaic dependency
  implementation(project(":packages:mosaic-core"))

  // Coroutines dependency for main source set
  implementation(libs.kotlinx.coroutines.core)

  // Testing dependencies - needed for main source set since this is a testing framework
  implementation(kotlin("test"))
  implementation(libs.kotlinx.coroutines.test)
  implementation(libs.junit.jupiter)
  implementation(libs.junit.jupiter.api)
  implementation(libs.junit.jupiter.engine)

  // Mocking and assertion libraries
  implementation(libs.mockk)
  implementation(libs.assertj)
}

kover {
  reports {
    verify {
      rule {
        minBound(90, CoverageUnit.LINE, AggregationType.COVERED_PERCENTAGE)
      }
      rule {
        minBound(80, CoverageUnit.BRANCH, AggregationType.COVERED_PERCENTAGE)
      }
      rule {
        minBound(90, CoverageUnit.INSTRUCTION, AggregationType.COVERED_PERCENTAGE)
      }
    }
  }
}
