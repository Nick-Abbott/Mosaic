@file:Suppress(
  "LongMethod",
  "FunctionMaxLength",
  "NestedBlockDepth",
  "MaxLineLength",
  "ktlint:standard:max-line-length",
)

package org.buildmosaic.gradle

import org.buildmosaic.analysis.SUMMARY_PATH
import org.buildmosaic.analysis.SummaryCodec
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.test.assertEquals

internal fun project(
  root: File,
  name: String,
  pluginJar: File,
  jars: List<File>,
  roots: List<String> = emptyList(),
  role: String = "APPLICATION",
  enforcement: String = "STANDARD",
): File =
  File(root, name).apply {
    mkdirs()
    File(this, "settings.gradle.kts").writeText(
      """
      pluginManagement {
        repositories { gradlePluginPortal(); mavenCentral() }
        resolutionStrategy.eachPlugin {
          if (requested.id.id == "org.jetbrains.kotlin.jvm") useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
        }
      }
      rootProject.name = "$name"
      """.trimIndent(),
    )
    val dependencies = jars.joinToString("\n") { "implementation(files(\"${it.invariantSeparatorsPath}\"))" }
    val configuredRoots = roots.joinToString("\n") { "roots.add(\"$it\")" }
    File(this, "build.gradle.kts").writeText(
      """
      plugins {
        kotlin("jvm") version "2.2.10"
        id("org.buildmosaic.analysis")
      }
      group = "fixture"
      repositories {
        flatDir { dirs("${pluginJar.parentFile.invariantSeparatorsPath}") }
        mavenCentral()
      }
      dependencies {
        $dependencies
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
      }
      mosaicAnalysis {
        role = org.buildmosaic.gradle.MosaicAnalysisRole.$role
        enforcement = org.buildmosaic.gradle.MosaicAnalysisEnforcement.$enforcement
        $configuredRoots
      }
      """.trimIndent(),
    )
  }

internal fun assertFreshEquivalent(
  project: File,
  incrementalReport: String,
  expectFailure: Boolean = false,
) {
  val summary = SummaryCodec.decode(File(project, "build/mosaic-analysis/main/summary.json").readBytes())
  val fresh = run(project, "clean build", expectFailure = expectFailure)
  assertEquals(TaskOutcome.SUCCESS, fresh.task(":extractMosaicMain")?.outcome)
  assertEquals(summary, SummaryCodec.decode(File(project, "build/mosaic-analysis/main/summary.json").readBytes()))
  assertEquals(incrementalReport, File(project, "build/reports/mosaic-analysis/main.txt").readText())
}

internal fun run(
  project: File,
  task: String,
  expectFailure: Boolean = false,
  configurationCache: Boolean = false,
  buildCache: Boolean = false,
): org.gradle.testkit.runner.BuildResult {
  // Count actual builds, including expected failures, in the captured JUnit output.
  System.err.println("MOSAIC_TESTKIT_INVOCATION")
  val runner =
    GradleRunner.create()
      .withProjectDir(project)
      .withArguments(
        task.split(' ') +
          listOf(
            "--offline",
            if (configurationCache) "--configuration-cache" else "--no-configuration-cache",
            if (buildCache) "--build-cache" else "--no-build-cache",
            "--stacktrace",
            "--gradle-user-home",
            File(System.getProperty("user.home"), ".gradle").absolutePath,
            "--info",
          ),
      )
      .withPluginClasspath()
  val result = if (expectFailure) runner.buildAndFail() else runner.build()
  val launches = result.output.lineSequence().count { it.contains("Mosaic K2 extraction launched") }
  assertEquals(0, launches, "Production integration launched a separate Mosaic K2 compiler")
  return result
}

internal fun jar(project: File): File = File(project, "build/libs/${project.name}.jar")

internal fun sha256(file: File): String =
  MessageDigest.getInstance(
    "SHA-256",
  ).digest(file.readBytes()).joinToString("") {
    "%02x".format(it)
  }

internal fun summaryHash(jar: File): String =
  JarFile(jar).use { archive ->
    val bytes = archive.getInputStream(archive.getJarEntry(SUMMARY_PATH)).readBytes()
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
  }

internal fun rewriteSummary(
  jar: File,
  replacement: ByteArray?,
) {
  val copy = File(jar.parentFile, "rewritten-${jar.name}")
  JarFile(jar).use { input ->
    JarOutputStream(copy.outputStream()).use { output ->
      var found = false
      input.entries().asSequence().forEach { entry ->
        if (entry.name == SUMMARY_PATH && replacement == null) return@forEach
        if (entry.name == SUMMARY_PATH) found = true
        output.putNextEntry(JarEntry(entry.name))
        if (!entry.isDirectory) {
          if (entry.name == SUMMARY_PATH) {
            output.write(
              replacement,
            )
          } else {
            input.getInputStream(entry).use { it.copyTo(output) }
          }
        }
        output.closeEntry()
      }
      if (!found && replacement != null) {
        output.putNextEntry(JarEntry(SUMMARY_PATH))
        output.write(replacement)
        output.closeEntry()
      }
    }
  }
  copy.copyTo(jar, overwrite = true)
  copy.delete()
}
