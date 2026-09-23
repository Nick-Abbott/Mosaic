@file:Suppress(
  "LongMethod",
  "LargeClass",
  "LongParameterList",
  "FunctionMaxLength",
  "MaxLineLength",
  "ktlint:standard:max-line-length",
)

package org.buildmosaic.gradle

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Files
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.Test
import kotlin.test.assertTrue

/** Exercises the public plugins DSL against publications, without TestKit's plugin classpath injection. */
class PublishedInstallationTest {
  @Test
  fun `published plugin installs and resolves its matching compiler artifact`() {
    val repositoryRoot = File(System.getProperty("user.dir")).parentFile
    val version =
      repositoryRoot.resolve("gradle.properties").readLines()
        .first { it.startsWith("mosaic.version=") }.substringAfter('=')
    val workspace = Files.createTempDirectory("mosaic-published-install-").toFile()
    val maven = workspace.resolve("maven")
    run(
      repositoryRoot,
      ":mosaic-analysis-core:publishAllPublicationsToInstallTestRepository",
      ":mosaic-compiler-plugin:publishAllPublicationsToInstallTestRepository",
      ":mosaic-gradle-plugin:publishAllPublicationsToInstallTestRepository",
      "-Pmosaic.installTestRepository=${maven.absolutePath}",
    )

    val marker =
      maven.resolve(
        "org/buildmosaic/analysis/org.buildmosaic.analysis.gradle.plugin/$version/org.buildmosaic.analysis.gradle.plugin-$version.pom",
      )
    assertTrue(marker.isFile, marker.absolutePath)
    assertTrue(marker.readText().contains("<groupId>org.buildmosaic.analysis</groupId>"))
    assertTrue(marker.readText().contains("<artifactId>mosaic-gradle-plugin</artifactId>"))
    for (artifact in listOf("mosaic-analysis-core", "mosaic-compiler-plugin", "mosaic-gradle-plugin")) {
      val directory = maven.resolve("org/buildmosaic/$artifact/$version")
      assertTrue(directory.resolve("$artifact-$version-sources.jar").isFile)
      assertTrue(directory.resolve("$artifact-$version-javadoc.jar").isFile)
      val pom = directory.resolve("$artifact-$version.pom").readText()
      assertTrue(pom.contains("<version>$version</version>"))
      assertTrue(!pom.contains("kotlin-compiler-embeddable"), pom)
    }
    val pluginPom =
      maven.resolve(
        "org/buildmosaic/mosaic-gradle-plugin/$version/mosaic-gradle-plugin-$version.pom",
      ).readText()
    assertTrue(pluginPom.contains("<artifactId>mosaic-analysis-core</artifactId>"))
    assertTrue(!pluginPom.contains("kotlin-gradle-plugin"), pluginPom)
    val compilerPom =
      maven.resolve("org/buildmosaic/mosaic-compiler-plugin/$version/mosaic-compiler-plugin-$version.pom").readText()
    assertTrue(compilerPom.contains("<artifactId>mosaic-analysis-core</artifactId>"))
    JarFile(maven.resolve("org/buildmosaic/mosaic-gradle-plugin/$version/mosaic-gradle-plugin-$version.jar")).use {
        jar ->
      val metadata =
        jar.getInputStream(jar.getJarEntry("org/buildmosaic/gradle/version.properties"))
          .bufferedReader().readText()
      assertTrue(metadata.contains("version=$version"))
    }
    val compilerJar =
      maven.resolve(
        "org/buildmosaic/mosaic-compiler-plugin/$version/mosaic-compiler-plugin-$version.jar",
      )
    assertTrue(compilerJar.isFile, compilerJar.absolutePath)
    JarFile(compilerJar).use { jar ->
      assertTrue(jar.getEntry("META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar") != null)
      assertTrue(jar.getEntry("META-INF/services/org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor") != null)
      assertTrue(
        jar.getInputStream(jar.getEntry("META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar"))
          .bufferedReader().readText().trim() == "org.buildmosaic.compiler.MosaicCompilerRegistrar",
      )
      assertTrue(
        jar.getInputStream(jar.getEntry("META-INF/services/org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor"))
          .bufferedReader().readText().trim() == "org.buildmosaic.compiler.MosaicCommandLineProcessor",
      )
      assertTrue(jar.getEntry("kotlinx/serialization/json/Json.class") != null)
      assertTrue(jar.entries().asSequence().none { it.name.startsWith("com/fasterxml/jackson/") })
    }

    val app = consumer(workspace, "app", maven, version, "2.2.10", "APPLICATION", "roots.add(\"app.entry()\")")
    source(app, "package app\nfun entry() = 1\n")
    val resolved =
      run(app, "build", "dependencies", "--configuration", "mosaicAnalysisCompilerPlugin", "--configuration-cache")
    assertTrue(resolved.output.contains("org.buildmosaic:mosaic-compiler-plugin:$version"), resolved.output)
    assertTrue(app.resolve("build/reports/mosaic-analysis/main.txt").readText().contains("FULLY VERIFIED"))
    val reused =
      run(app, "build", "dependencies", "--configuration", "mosaicAnalysisCompilerPlugin", "--configuration-cache")
    assertTrue(reused.output.contains("Configuration cache entry reused"), reused.output)

    val library = consumer(workspace, "library", maven, version, "2.2.10", "LIBRARY", "")
    source(library, "package library\nfun useful() = 1\n")
    run(library, "build", "--configuration-cache")
    JarFile(library.resolve("build/libs/library-99.0.0.jar")).use { jar ->
      assertTrue(jar.getEntry("META-INF/mosaic-analysis/v1/summary.json") != null)
    }
    assertTrue(library.resolve("build/reports/mosaic-analysis/main.txt").readText().contains("EXPORT_ONLY"))

    val mismatched = consumer(workspace, "mismatched", maven, version, "2.2.10", "LIBRARY", "")
    source(mismatched, "package mismatched\nfun useful() = 1\n")
    val badManifest =
      Manifest().apply {
        mainAttributes.putValue("Manifest-Version", "1.0")
        mainAttributes.putValue("Implementation-Version", "9.9.9")
      }
    JarOutputStream(mismatched.resolve("wrong-compiler.jar").outputStream(), badManifest).close()
    mismatched.resolve(
      "build.gradle.kts",
    ).appendText(
      "\ntasks.named<org.buildmosaic.gradle.ExtractMosaicTask>(\"extractMosaicMain\") { compilerPluginJar.set(file(\"wrong-compiler.jar\")) }\n",
    )
    assertTrue(
      run(
        mismatched,
        "extractMosaicMain",
        expectFailure = true,
      ).output.contains("Mosaic compiler plugin version mismatch"),
    )

    val unsupported = consumer(workspace, "unsupported", maven, version, "2.2.0", "LIBRARY", "")
    source(unsupported, "package unsupported\nfun useful() = 1\n")
    assertTrue(
      run(
        unsupported,
        "extractMosaicMain",
        expectFailure = true,
      ).output.contains("requires Kotlin Gradle plugin 2.2.10"),
    )

    compilerJar.delete()
    val missing = consumer(workspace, "missing", maven, version, "2.2.10", "LIBRARY", "")
    source(missing, "package missing\nfun useful() = 1\n")
    val missingResult = run(missing, "extractMosaicMain", "--refresh-dependencies", expectFailure = true)
    assertTrue(missingResult.output.contains("mosaic-compiler-plugin:$version"), missingResult.output)
    assertTrue(
      missingResult.output.contains("Could not resolve") || missingResult.output.contains("Could not find"),
      missingResult.output,
    )
  }

  private fun consumer(
    workspace: File,
    name: String,
    maven: File,
    mosaicVersion: String,
    kotlinVersion: String,
    role: String,
    roots: String,
  ): File =
    workspace.resolve(name).apply {
      mkdirs()
      resolve("settings.gradle.kts").writeText(
        """
        pluginManagement { repositories { maven(url = uri("${maven.invariantSeparatorsPath}")); gradlePluginPortal() } }
        dependencyResolutionManagement { repositories { maven(url = uri("${maven.invariantSeparatorsPath}")); mavenCentral() } }
        rootProject.name = "$name"
        """.trimIndent(),
      )
      resolve("build.gradle.kts").writeText(
        """
        plugins {
          kotlin("jvm") version "$kotlinVersion"
          id("org.buildmosaic.analysis") version "$mosaicVersion"
        }
        version = "99.0.0"
        mosaicAnalysis {
            role = org.buildmosaic.gradle.MosaicAnalysisRole.$role
            enforcement = org.buildmosaic.gradle.MosaicAnalysisEnforcement.STANDARD
            $roots
          }
        """.trimIndent(),
      )
    }

  private fun source(
    project: File,
    text: String,
  ) {
    project.resolve("src/main/kotlin/Main.kt").apply {
      parentFile.mkdirs()
      writeText(text)
    }
  }

  private fun run(
    project: File,
    vararg arguments: String,
    expectFailure: Boolean = false,
  ): org.gradle.testkit.runner.BuildResult {
    val runner =
      GradleRunner.create().withProjectDir(project)
        .withArguments(
          *arguments,
          "--stacktrace",
          "--gradle-user-home",
          File(System.getProperty("user.home"), ".gradle").absolutePath,
        )
    return if (expectFailure) runner.buildAndFail() else runner.build()
  }
}
