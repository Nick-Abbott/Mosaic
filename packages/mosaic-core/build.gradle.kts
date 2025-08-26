/*
 * Mosaic Core module build.gradle.kts
 * This module contains the main Mosaic functionality
 */

// This module inherits all configuration from packages/build.gradle.kts
// Additional module-specific configuration can be added here

dependencies {
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlinx.coroutines.test)
}
