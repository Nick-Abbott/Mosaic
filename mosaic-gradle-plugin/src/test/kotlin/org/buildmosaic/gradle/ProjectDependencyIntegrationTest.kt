@file:Suppress(
  "LongMethod",
  "FunctionMaxLength",
  "NestedBlockDepth",
  "MaxLineLength",
  "ktlint:standard:max-line-length",
)

package org.buildmosaic.gradle

import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectDependencyIntegrationTest {
  @Test
  fun `project dependency jar variant supplies its Mosaic summary`() {
    val root = Files.createTempDirectory("mosaic-project-dependency").toFile()
    val repository = File(System.getProperty("user.dir")).parentFile
    val pluginJar = File(repository, "mosaic-compiler-plugin/build/libs/mosaic-compiler-plugin-${mosaicVersion()}.jar")
    val coreJar = File(repository, "mosaic-core/build/libs/mosaic-core-${mosaicVersion()}.jar")
    File(root, "settings.gradle.kts").writeText(
      """
      pluginManagement {
        repositories { gradlePluginPortal(); mavenCentral() }
        resolutionStrategy.eachPlugin {
          if (requested.id.id == "org.jetbrains.kotlin.jvm") useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
        }
      }
      rootProject.name = "project-dependency"
      include("producer", "consumer")
      """.trimIndent(),
    )
    listOf("producer", "consumer").forEach { name ->
      val module = File(root, name).apply { mkdirs() }
      File(module, "build.gradle.kts").writeText(
        """
        plugins { kotlin("jvm") version "2.2.10"; id("org.buildmosaic.analysis") }
        group = "fixture"
        repositories {
          flatDir { dirs("${pluginJar.parentFile.invariantSeparatorsPath}") }
          mavenCentral()
        }
        dependencies {
          implementation(files("${coreJar.invariantSeparatorsPath}"))
          implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
          ${if (name == "consumer") "implementation(project(\":producer\"))" else ""}
        }
        mosaicAnalysis {
          ${if (name == "producer") "role = org.buildmosaic.gradle.MosaicAnalysisRole.LIBRARY" else "roots.add(\"consumer.entry()\")"}
        }
        """.trimIndent(),
      )
    }
    File(root, "producer/src/main/kotlin/Producer.kt").apply {
      parentFile.mkdirs()
      writeText(
        """
        package producer
        import org.buildmosaic.core.injection.*
        class Metrics
        suspend fun base(): Canvas = canvas { single<Metrics> { Metrics() } }
        """.trimIndent(),
      )
    }
    File(root, "consumer/src/main/kotlin/Consumer.kt").apply {
      parentFile.mkdirs()
      writeText(
        """
        package consumer
        import org.buildmosaic.core.*
        import org.buildmosaic.core.injection.*
        import producer.*
        val MetricsTile = singleTile { source<Metrics>() }
        suspend fun entry(): Metrics = base().create().compose(MetricsTile)
        """.trimIndent(),
      )
    }
    val result = run(root, ":consumer:verifyMosaicMain", configurationCache = true)
    assertEquals(TaskOutcome.SUCCESS, result.task(":consumer:verifyMosaicMain")?.outcome)
    assertTrue(File(root, "consumer/build/reports/mosaic-analysis/main.txt").readText().contains("FULLY VERIFIED"))
  }
}
