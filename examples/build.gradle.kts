plugins {
  id("kotlin.convention") apply false
  id("quality.convention") apply false
//  id("com.abbott.mosaic.testing") apply false
}

subprojects {
  apply(plugin = "kotlin.convention")
  apply(plugin = "quality.convention")
//  apply(plugin = "com.abbott.mosaic.testing")

  // Fix race condition: Ensure mosaic-build is fully built before KSP runs
  // This prevents "SymbolProcessorProvider not found" errors in parallel CI builds
  afterEvaluate {
    pluginManager.withPlugin("com.google.devtools.ksp") {
      tasks.named("kspKotlin") {
        dependsOn(":mosaic-build:jar")
      }
    }
  }
}

