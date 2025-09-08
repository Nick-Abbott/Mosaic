import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

description = "Gradle plugin adding mosaic support to a consumer project"

plugins {
  id("gradle-plugin.convention")
}

// Generate a Kotlin file containing the Mosaic version so the plugin can
// reference it at compile time when applying the BOM.
val generateVersionFile =
  tasks.register("generateMosaicVersion") {
    val outputDir = layout.buildDirectory.dir("generated/mosaic-version")
    val version = project.property("mosaic.version") as String
    outputs.dir(outputDir)

    doLast {
      val versionFile =
        outputDir.get().file("org/buildmosaic/gradle/plugin/MosaicVersion.kt").asFile
      versionFile.parentFile.mkdirs()
      versionFile.writeText(
        (
          """
          package org.buildmosaic.gradle.plugin

          internal const val MOSAIC_VERSION: String = "$version"
          """.trimIndent() + "\n"
        ),
      )
    }
  }

extensions.configure<KotlinProjectExtension> {
  sourceSets.getByName("main").kotlin.srcDir(generateVersionFile)
}

tasks.named("compileKotlin") { dependsOn(generateVersionFile) }

dependencies {
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)
  implementation(libs.ksp)

  compileOnly(gradleApi())
  compileOnly(libs.kotlin.gradle.plugin)
  compileOnly(libs.ksp.gradle.plugin)
}

gradlePlugin {
  plugins {
    create("mosaicConsumer") {
      id = "org.buildmosaic.consumer"
      implementationClass = "org.buildmosaic.gradle.plugin.MosaicConsumerPlugin"
      displayName = "Mosaic Consumer Plugin"
      description = project.description
      tags.set(
        listOf(
          "consumer",
          "ksp",
          "symbol-processing",
        ),
      )
    }
  }
}
