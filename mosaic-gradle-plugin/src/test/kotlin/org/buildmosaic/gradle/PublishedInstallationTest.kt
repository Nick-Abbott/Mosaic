@file:Suppress("LongMethod", "MaxLineLength", "ktlint:standard:max-line-length")

package org.buildmosaic.gradle

import org.buildmosaic.analysis.SUMMARY_PATH
import org.buildmosaic.analysis.SummaryCodec
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Files
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Proves that the real publications install through the external plugins DSL. */
class PublishedInstallationTest {
  @Test
  fun `published plugin verifies Canvas root`() {
    val repositoryRoot = File(System.getProperty("user.dir")).parentFile
    val version =
      repositoryRoot.resolve("gradle.properties").readLines()
        .first { it.startsWith("mosaic.version=") }.substringAfter('=')
    val workspace = Files.createTempDirectory("mosaic-published-install-").toFile()
    val maven = workspace.resolve("maven")
    run(
      repositoryRoot,
      ":mosaic-core:publishAllPublicationsToInstallTestRepository",
      ":mosaic-analysis-core:publishAllPublicationsToInstallTestRepository",
      ":mosaic-compiler-plugin:publishAllPublicationsToInstallTestRepository",
      ":mosaic-gradle-plugin:publishAllPublicationsToInstallTestRepository",
      "-Pmosaic.installTestRepository=${maven.absolutePath}",
    )

    val marker =
      maven.resolve(
        "org/buildmosaic/analysis/org.buildmosaic.analysis.gradle.plugin/$version/org.buildmosaic.analysis.gradle.plugin-$version.pom",
      ).readText()
    assertTrue(marker.contains("<groupId>org.buildmosaic</groupId>"))
    assertTrue(marker.contains("<artifactId>mosaic-gradle-plugin</artifactId>"))
    assertTrue(marker.contains("<version>$version</version>"))
    val pluginPom =
      maven.resolve("org/buildmosaic/mosaic-gradle-plugin/$version/mosaic-gradle-plugin-$version.pom").readText()
    assertTrue(pluginPom.contains("<artifactId>mosaic-analysis-core</artifactId>"))
    assertTrue(!pluginPom.contains("kotlin-gradle-plugin"), pluginPom)
    val compilerPom =
      maven.resolve("org/buildmosaic/mosaic-compiler-plugin/$version/mosaic-compiler-plugin-$version.pom").readText()
    assertTrue(!compilerPom.contains("<artifactId>mosaic-analysis-core</artifactId>"), compilerPom)
    assertTrue(!compilerPom.contains("kotlin-compiler-embeddable"), compilerPom)
    JarFile(maven.resolve("org/buildmosaic/mosaic-compiler-plugin/$version/mosaic-compiler-plugin-$version.jar")).use {
        jar ->
      for ((service, implementation) in listOf(
        "CompilerPluginRegistrar" to "MosaicCompilerRegistrar",
        "CommandLineProcessor" to "MosaicCommandLineProcessor",
      )) {
        val entry = jar.getJarEntry("META-INF/services/org.jetbrains.kotlin.compiler.plugin.$service")
        assertTrue(entry != null)
        assertEquals(
          "org.buildmosaic.compiler.$implementation",
          jar.getInputStream(entry).bufferedReader().readText().trim(),
        )
      }
      assertTrue(jar.getEntry("kotlinx/serialization/json/Json.class") != null)
      assertTrue(jar.entries().asSequence().none { it.name.startsWith("com/fasterxml/jackson/") })
    }

    val consumer = workspace.resolve("app").apply { mkdirs() }
    consumer.resolve("settings.gradle.kts").writeText(
      """
      pluginManagement { repositories { maven(url = uri("${maven.invariantSeparatorsPath}")); gradlePluginPortal() } }
      dependencyResolutionManagement { repositories { maven(url = uri("${maven.invariantSeparatorsPath}")); mavenCentral() } }
      rootProject.name = "app"
      """.trimIndent(),
    )
    consumer.resolve("build.gradle.kts").writeText(
      """
      plugins {
        kotlin("jvm") version "2.2.10"
        id("org.buildmosaic.analysis") version "$version"
      }
      version = "99.0.0"
      dependencies { implementation("org.buildmosaic:mosaic-core:$version") }
      mosaicAnalysis {
        role = org.buildmosaic.gradle.MosaicAnalysisRole.APPLICATION
        enforcement = org.buildmosaic.gradle.MosaicAnalysisEnforcement.STANDARD
        roots.add("app.entry()")
      }
      """.trimIndent(),
    )
    consumer.resolve("src/main/kotlin/Main.kt").apply {
      parentFile.mkdirs()
      writeText(
        """
        package app
        import org.buildmosaic.core.injection.*
        suspend fun entry(): String = canvas { single<String> { "ready" } }.source<String>()
        """.trimIndent(),
      )
    }
    val result = run(consumer, "build", "dependencies", "--configuration", "kotlinCompilerPluginClasspathMain")
    assertEquals(TaskOutcome.SUCCESS, result.task(":verifyMosaicMain")?.outcome)
    assertTrue(result.output.contains("org.buildmosaic:mosaic-compiler-plugin:$version"), result.output)
    val report = consumer.resolve("build/reports/mosaic-analysis/main.txt").readText()
    assertTrue(report.contains("Result: FULLY VERIFIED"), report)
    assertTrue(report.contains("VERIFIED REQUIRED_LOOKUP"), report)
    val summary = SummaryCodec.decode(consumer.resolve("build/mosaic-analysis/main/summary.json").readBytes())
    assertEquals(3, summary.formatVersion)
    assertEquals("analysis-contract-2", summary.semanticsVersion)
    assertEquals("prototype-10", summary.toolVersion)
    assertTrue(summary.module.callables.any { it.id == "app.entry()" && it.effects.isNotEmpty() })
    JarFile(consumer.resolve("build/libs/app-99.0.0.jar")).use { assertTrue(it.getEntry(SUMMARY_PATH) != null) }
  }

  private fun run(
    project: File,
    vararg arguments: String,
  ): org.gradle.testkit.runner.BuildResult =
    GradleRunner.create().withProjectDir(project)
      .withArguments(
        *arguments,
        "--stacktrace",
        "--gradle-user-home",
        File(System.getProperty("user.home"), ".gradle").absolutePath,
      )
      .build()
}
