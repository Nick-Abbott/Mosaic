import java.util.Properties

description = "Compiler-independent semantic analysis for Mosaic contracts"

plugins {
  id("kotlin.convention")
  id("quality.convention")
  id("testing.convention")
  `java-library`
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  implementation(libs.kotlinx.serialization.json)
}

val capabilityDefinition = rootProject.layout.projectDirectory.file("compatibility/runtime-capabilities.properties")
val capabilitySource = layout.buildDirectory.dir("generated/runtime-capabilities")
val generateRuntimeCapability =
  tasks.register("generateRuntimeCapability") {
    inputs.file(capabilityDefinition)
    outputs.dir(capabilitySource)
    val definition = capabilityDefinition.asFile
    val outputSource = capabilitySource.get().file("org/buildmosaic/analysis/RuntimeCapability.kt").asFile
    doLast {
      val capability =
        Properties().apply { definition.inputStream().use(::load) }
          .getProperty("canvasAnalysis")
      outputSource.apply {
        parentFile.mkdirs()
        writeText("package org.buildmosaic.analysis\n\nconst val CANVAS_ANALYSIS_CAPABILITY = \"$capability\"\n")
      }
    }
  }
kotlin.sourceSets.main { kotlin.srcDir(generateRuntimeCapability) }
