description = "The core tile composition library for Mosaic"

plugins {
  id("kotlin.convention")
  id("quality.convention")
  id("testing.convention")
  id("library.convention")
}

dependencies {
  api(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlinx.coroutines.test)
}

// The cross-module test bridge is unsupported implementation plumbing, not consumer API.
dokka {
  dokkaSourceSets.configureEach {
    perPackageOption {
      matchingRegex.set("org\\.buildmosaic\\.core\\.internal.*")
      suppress.set(true)
    }
  }
}
