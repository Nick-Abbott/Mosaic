/*
 * Root build script for Mosaic example projects.
 * Individual example modules can be added as subprojects.
 */
plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  id("com.abbott.mosaic.kotlin") apply false
  id("com.abbott.mosaic.quality") apply false
  id("com.abbott.mosaic.testing") apply false
}

subprojects {
  apply(plugin = "com.abbott.mosaic.kotlin")
  apply(plugin = "com.abbott.mosaic.quality")
  apply(plugin = "com.abbott.mosaic.testing")
}

// Common configuration for all example subprojects can go here.
