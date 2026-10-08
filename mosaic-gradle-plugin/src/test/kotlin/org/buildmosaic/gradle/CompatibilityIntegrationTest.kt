@file:Suppress(
  "LongMethod",
  "FunctionMaxLength",
  "MaxLineLength",
  "ktlint:standard:max-line-length",
  "NestedBlockDepth",
)

package org.buildmosaic.gradle

import org.buildmosaic.analysis.CANVAS_ANALYSIS_CAPABILITY
import org.buildmosaic.analysis.ModuleContract
import org.buildmosaic.analysis.ProducerIdentity
import org.buildmosaic.analysis.ProductionContext
import org.buildmosaic.analysis.RUNTIME_DESCRIPTOR_PATH
import org.buildmosaic.analysis.RuntimeRequirement
import org.buildmosaic.analysis.SUMMARY_PATH
import org.buildmosaic.analysis.SummaryCodec
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatibilityIntegrationTest {
  private val repository = File(System.getProperty("user.dir")).parentFile
  private val plugin =
    File(repository, "mosaic-compiler-plugin/build/libs/mosaic-compiler-plugin-${mosaicVersion()}.jar")
  private val core = File(repository, "mosaic-core/build/libs/mosaic-core-${mosaicVersion()}.jar")

  @Test
  fun `new Runtime capability semantics govern application library graph packaging and NO_SOURCE`() {
    val root = Files.createTempDirectory("mosaic-runtime-protocol").toFile()
    val runtime = File(root, "selected-runtime.jar")
    runtimeCandidate(core, runtime)
    val app = project(root, "application", plugin, listOf(runtime), roots = listOf("entry()"))
    File(app, "src/main/kotlin/Entry.kt").apply {
      parentFile.mkdirs()
      writeText("fun entry() = 1")
    }
    val build = run(app, "build mosaicGraph", configurationCache = true)
    assertEquals(TaskOutcome.SUCCESS, build.task(":admitMosaicMain")?.outcome)
    val summary = localSummary(app)
    assertEquals("99.0.0", summary.runtimes.single().runtimeVersion)
    assertEquals(mosaicVersion(), summary.producer.analysisVersion)
    assertEquals("2.4.20", summary.producer.compilerVersion)
    JarFile(jar(app)).use { archive ->
      assertEquals(summary, SummaryCodec.decode(archive.getInputStream(archive.getJarEntry(SUMMARY_PATH)).readBytes()))
    }
    assertTrue(File(app, "build/reports/mosaic-analysis/main.txt").readText().contains("FULLY VERIFIED"))
    val library = project(root, "library", plugin, listOf(runtime), role = "LIBRARY")
    val empty = run(library, "jar mosaicGraph verifyMosaicMain", configurationCache = true)
    assertEquals(TaskOutcome.NO_SOURCE, empty.task(":compileKotlin")?.outcome)
    assertTrue(localSummary(library).module.callables.isEmpty())
    assertEquals(summary.runtimes, localSummary(library).runtimes)
    runtimeCandidate(core, runtime, version = "100.0.0")
    val changedRuntime = run(app, "build mosaicGraph", configurationCache = true)
    assertEquals(TaskOutcome.SUCCESS, changedRuntime.task(":compileKotlin")?.outcome)
    assertEquals("100.0.0", localSummary(app).runtimes.single().runtimeVersion)
    assertFreshEquivalent(app, File(app, "build/reports/mosaic-analysis/main.txt").readText())
    runtimeCandidate(core, runtime, unknown = true)
    val applicationFailure = run(app, "build mosaicGraph", expectFailure = true, configurationCache = true)
    assertTrue(
      applicationFailure.output.contains("Unknown required Mosaic Runtime capability"),
      applicationFailure.output,
    )
    assertNoTrustedOutputs(app)
    listOf("compileKotlin", "extractMosaicMain", "verifyMosaicMain", "mosaicGraph", "jar").forEach { entry ->
      val failure = run(library, entry, expectFailure = true, configurationCache = true)
      assertEquals(TaskOutcome.FAILED, failure.task(":admitMosaicMain")?.outcome)
      assertTrue(failure.output.contains("mosaic.future/1"), failure.output)
      assertNoTrustedOutputs(library)
    }
  }

  @Test
  fun `older producer requirements survive library boundaries and unknown retained requirements fail STANDARD`() {
    val root = Files.createTempDirectory("mosaic-retained-protocol").toFile()
    val selected =
      RuntimeRequirement("org.buildmosaic:mosaic-core", mosaicVersion(), listOf(CANVAS_ANALYSIS_CAPABILITY))
    val retained = RuntimeRequirement("org.buildmosaic:mosaic-test", "99.0.0", listOf(CANVAS_ANALYSIS_CAPABILITY))
    val context = ProductionContext(ProducerIdentity("0.6.1-analysis", "2.2.21"), listOf(selected, retained))
    val dependency = File(root, "older-library.jar")
    val bytes = SummaryCodec.encode(ModuleContract("fixture:older"), context)
    JarOutputStream(dependency.outputStream()).use { archive ->
      archive.putNextEntry(JarEntry(SUMMARY_PATH))
      archive.write(bytes)
      archive.closeEntry()
    }
    val library = project(root, "library", plugin, listOf(core, dependency), role = "LIBRARY")
    run(library, "build", configurationCache = true)
    assertEquals(listOf(selected, retained), localSummary(library).runtimes)
    val app = project(root, "application", plugin, listOf(core, jar(library)), roots = listOf("entry()"))
    File(app, "src/main/kotlin/Entry.kt").apply {
      parentFile.mkdirs()
      writeText("fun entry() = 1")
    }
    run(app, "build", configurationCache = true)
    assertEquals(listOf(selected, retained), localSummary(app).runtimes)
    val before = localSummary(app)
    // An ordinary summary body changes verification without changing extraction requirements.
    val changedBody =
      SummaryCodec.encode(
        ModuleContract("fixture:older"),
        context,
        limitations = listOf("diagnostic boundary"),
      )
    rewriteSummary(dependency, changedBody)
    val ordinary = run(library, "build", configurationCache = true)
    assertEquals(TaskOutcome.NO_SOURCE, ordinary.task(":compileKotlin")?.outcome)
    assertEquals(TaskOutcome.UP_TO_DATE, ordinary.task(":extractMosaicMain")?.outcome)
    assertEquals(before.runtimes, localSummary(library).runtimes)
    val unknown =
      changedBody.decodeToString().replace(
        "\"requires\":[\"mosaic.canvas-analysis/1\"]",
        "\"requires\":[\"mosaic.canvas-analysis/1\",\"mosaic.future/1\"]",
      )
    rewriteSummary(dependency, rehash(unknown))
    val failure = run(library, "jar", expectFailure = true, configurationCache = true)
    assertTrue(failure.output.contains("Unknown required Mosaic Runtime capability"), failure.output)
    assertNoTrustedOutputs(library)
  }

  @Test
  fun `compile and runtime selections must align and stale shards cannot be promoted`() {
    val root = Files.createTempDirectory("mosaic-alignment-protocol").toFile()
    val runtime = File(root, "newer-runtime.jar")
    runtimeCandidate(core, runtime)
    val app = project(root, "alignment", plugin, listOf(core), roots = listOf("entry()"))
    val source =
      File(app, "src/main/kotlin/Entry.kt").apply {
        parentFile.mkdirs()
        writeText("fun entry() = 1")
      }
    val script = File(app, "build.gradle.kts")
    val original = script.readText()
    run(app, "build", configurationCache = true)
    source.writeText("fun entry() = 2")
    val stale = run(app, "extractMosaicMain -x compileKotlin", expectFailure = true)
    assertTrue(stale.output.contains("Stale Mosaic shard source content"), stale.output)
    assertFalse(File(app, "build/mosaic-analysis/main/summary.json").exists())
    run(app, "build")
    script.writeText(
      original.replace(
        "implementation(files(\"${core.invariantSeparatorsPath}\"))",
        "compileOnly(files(\"${core.invariantSeparatorsPath}\"))\nruntimeOnly(files(\"${runtime.invariantSeparatorsPath}\"))",
      ),
    )
    val mismatch = run(app, "jar", expectFailure = true)
    assertTrue(mismatch.output.contains("Align compileClasspath and runtimeClasspath"), mismatch.output)
    assertNoTrustedOutputs(app)
    script.writeText(original)
    run(app, "build")
    assertFreshEquivalent(app, File(app, "build/reports/mosaic-analysis/main.txt").readText())
    val shadow = File(root, "runtime-shadow.jar")
    runtimeCandidate(core, shadow, unknown = true)
    script.appendText(
      "\n" +
        """
        repositories {
          exclusiveContent {
            forRepository { flatDir { dirs("${root.invariantSeparatorsPath}") } }
            filter { includeModule("fixture", "runtime-shadow") }
          }
        }
        dependencies { runtimeOnly("fixture:runtime-shadow:99.0.0") }
        """.trimIndent(),
    )
    val substitutedRuntime = run(app, "jar", expectFailure = true)
    assertTrue(
      substitutedRuntime.output.contains("Unknown required Mosaic Runtime capability"),
      substitutedRuntime.output,
    )
    assertNoTrustedOutputs(app)
  }
}

private fun localSummary(project: File) =
  SummaryCodec.decode(
    File(project, "build/mosaic-analysis/main/summary.json").readBytes(),
  )

private fun assertNoTrustedOutputs(project: File) {
  assertFalse(File(project, "build/mosaic-analysis/main/summary.json").exists())
  assertFalse(jar(project).exists())
  assertFalse(File(project, "build/reports/mosaic-analysis/graph.md").exists())
  assertFalse(File(project, "build/mosaic-analysis/main/shards").exists())
}

private fun rehash(text: String): ByteArray {
  val empty = text.replace(Regex("\"integrityHash\":\"[0-9a-f]+\""), "\"integrityHash\":\"\"").trimEnd()
  val hash = MessageDigest.getInstance("SHA-256").digest(empty.toByteArray()).joinToString("") { "%02x".format(it) }
  return empty.replace("\"integrityHash\":\"\"", "\"integrityHash\":\"$hash\"").toByteArray()
}

private fun runtimeCandidate(
  original: File,
  output: File,
  unknown: Boolean = false,
  version: String = "99.0.0",
) {
  JarFile(original).use { input ->
    val manifest = Manifest(input.manifest)
    manifest.mainAttributes.putValue("Implementation-Version", version)
    JarOutputStream(output.outputStream(), manifest).use { archive ->
      input.entries().asSequence().filter { it.name != "META-INF/MANIFEST.MF" }.forEach { entry ->
        archive.putNextEntry(JarEntry(entry.name))
        if (entry.name == RUNTIME_DESCRIPTOR_PATH) {
          val requirements = if (unknown) "\"mosaic.canvas-analysis/1\",\"mosaic.future/1\"" else "\"mosaic.canvas-analysis/1\""
          archive.write(
            """{"descriptorVersion":1,"module":"org.buildmosaic:mosaic-core","runtimeVersion":"$version","requires":[$requirements]}""".toByteArray(),
          )
        } else if (!entry.isDirectory) {
          input.getInputStream(entry).use { it.copyTo(archive) }
        }
        archive.closeEntry()
      }
    }
  }
}
