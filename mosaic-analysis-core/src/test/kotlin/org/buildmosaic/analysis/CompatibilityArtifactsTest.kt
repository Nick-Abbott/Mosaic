package org.buildmosaic.analysis

import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Suppress("FunctionMaxLength")
class CompatibilityArtifactsTest {
  private val descriptor =
    """{"descriptorVersion":1,"module":"org.buildmosaic:mosaic-core",""" +
      """"runtimeVersion":"candidate","requires":["mosaic.canvas-analysis/1"]}"""

  @Test
  fun `selected artifact identity missing descriptors and ownership are validated`() {
    val root = Files.createTempDirectory("mosaic-artifact-protocol").toFile()
    val unrelated = artifact(root, "unrelated", emptyMap())
    assertTrue(CompatibilityArtifacts.runtimes(listOf(unrelated)).isEmpty())
    assertTrue(CompatibilityArtifacts.summaries(listOf(unrelated)).isEmpty())
    assertTrue(CompatibilityArtifacts.runtimes(listOf(File(root, "absent-resources"))).isEmpty())
    assertFailsWith<IllegalArgumentException> { CompatibilityArtifacts.runtimes(listOf(File(root, "missing.jar"))) }
    val runtime =
      artifact(
        root,
        "runtime",
        mapOf(
          "org/buildmosaic/core/injection/Canvas.class" to byteArrayOf(),
          RUNTIME_DESCRIPTOR_PATH to descriptor.toByteArray(),
        ),
      )
    assertEquals("candidate", CompatibilityArtifacts.runtimes(listOf(runtime)).single().runtimeVersion)
    val missing = artifact(root, "missing", mapOf("org/buildmosaic/core/injection/Canvas.class" to byteArrayOf()))
    assertFailsWith<IllegalArgumentException> { CompatibilityArtifacts.runtimes(listOf(missing)) }
    val wrong = artifact(root, "wrong", mapOf(RUNTIME_DESCRIPTOR_PATH to descriptor.toByteArray()))
    assertFailsWith<IllegalArgumentException> { CompatibilityArtifacts.runtimes(listOf(wrong)) }
    val duplicate =
      artifact(
        root,
        "duplicate",
        mapOf(
          "org/buildmosaic/core/injection/Canvas.class" to byteArrayOf(),
          RUNTIME_DESCRIPTOR_PATH to descriptor.toByteArray(),
        ),
      )
    assertFailsWith<IllegalArgumentException> { CompatibilityArtifacts.runtimes(listOf(runtime, duplicate)) }
    val manifest =
      Manifest().apply {
        mainAttributes.putValue("Manifest-Version", "1.0")
        mainAttributes.putValue("Implementation-Version", "different")
      }
    val inconsistent =
      artifact(
        root,
        "inconsistent",
        mapOf(
          "org/buildmosaic/core/injection/Canvas.class" to byteArrayOf(),
          RUNTIME_DESCRIPTOR_PATH to descriptor.toByteArray(),
        ),
        manifest,
      )
    assertFailsWith<IllegalArgumentException> { CompatibilityArtifacts.runtimes(listOf(inconsistent)) }
  }

  @Test
  fun `present incompatible contracts and conflicting declaration owners fail explicitly`() {
    val root = Files.createTempDirectory("mosaic-contract-artifacts").toFile()
    val bad = artifact(root, "bad", mapOf(SUMMARY_PATH to """{"formatVersion":5}""".toByteArray()))
    assertFailsWith<IllegalArgumentException> { CompatibilityArtifacts.summaries(listOf(bad)) }
    val site = SourceLocation("library", "Library.kt", 1, 1)
    val owner = CallableContract("shared()", effects = emptyList(), site = site)
    assertFailsWith<IllegalArgumentException> {
      SummaryCodec.encode(ModuleContract("duplicate", callables = listOf(owner, owner)), fixtureContext)
    }
    val first =
      artifact(
        root,
        "first",
        mapOf(SUMMARY_PATH to SummaryCodec.encode(ModuleContract("first", callables = listOf(owner)), fixtureContext)),
      )
    val second =
      artifact(
        root,
        "second",
        mapOf(SUMMARY_PATH to SummaryCodec.encode(ModuleContract("second", callables = listOf(owner)), fixtureContext)),
      )
    assertFailsWith<IllegalArgumentException> { CompatibilityArtifacts.summaries(listOf(first, second)) }
  }
}

private fun artifact(
  root: File,
  name: String,
  resources: Map<String, ByteArray>,
  manifest: Manifest = Manifest(),
): File =
  File(root, "$name.jar").also { output ->
    JarOutputStream(output.outputStream(), manifest).use { jar ->
      resources.forEach { (path, bytes) ->
        jar.putNextEntry(JarEntry(path))
        jar.write(bytes)
        jar.closeEntry()
      }
    }
  }
