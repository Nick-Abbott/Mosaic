package org.buildmosaic.compiler

import org.buildmosaic.analysis.RUNTIME_DESCRIPTOR_PATH
import org.buildmosaic.analysis.SummaryCodec
import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("FunctionMaxLength")
class CompatibilityAdmissionTest {
  @Test
  fun `direct compiler admits newer known Runtime and rejects unknown semantics without stale output`() {
    val root = Files.createTempDirectory("mosaic-direct-admission").toFile()
    val runtime = File(root, "new-runtime.jar")
    val classpath = System.getProperty("mosaic.fixture.classpath").split(File.pathSeparator).map(::File)
    val core = classpath.single { it.name.startsWith("mosaic-core-") && it.extension == "jar" }
    val selected = classpath.map { if (it == core) runtime else it }.joinToString(File.pathSeparator) { it.path }
    val source = File(root, "Entry.kt").apply { writeText("fun entry() = 1") }
    val summary = File(root, "summary.json")
    val args =
      listOf(
        "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", selected,
        "-Xplugin=${System.getProperty("mosaic.plugin.jar")}",
        "-P", "plugin:org.buildmosaic.analysis:output=${summary.path}",
        "-P", "plugin:org.buildmosaic.analysis:module=fixture:direct", "-d", File(root, "classes").path, source.path,
      )
    newerRuntime(core, runtime, false)
    val success = compileProtocolFixture(args)
    assertTrue(success.first, success.second)
    val contract = SummaryCodec.decode(summary.readBytes())
    assertEquals("99.0.0", contract.runtimes.single { it.module.endsWith(":mosaic-core") }.runtimeVersion)
    assertEquals(System.getProperty("mosaic.compat.compiler.version", "2.4.20"), contract.producer.compilerVersion)
    val analysisVersion =
      JarFile(File(System.getProperty("mosaic.plugin.jar"))).use {
        it.manifest.mainAttributes.getValue("Implementation-Version")
      }
    assertEquals(analysisVersion, contract.producer.analysisVersion)
    newerRuntime(core, runtime, true)
    val failure = compileProtocolFixture(args)
    assertFalse(failure.first, failure.second)
    assertTrue(failure.second.contains("Unknown required Mosaic Runtime capability"), failure.second)
    assertFalse(summary.exists())
  }

  @Test
  fun `direct invocation cannot replace artifact admission with an asserted context`() {
    val root = Files.createTempDirectory("mosaic-direct-context").toFile()
    val source = File(root, "Entry.kt").apply { writeText("fun entry() = 1") }
    val summary = File(root, "summary.json").apply { writeText("old successful output") }
    val context = File(root, "context.json").apply { writeText("""{"validated":true}""") }
    val args =
      listOf(
        "-no-stdlib", "-no-reflect", "-classpath", System.getProperty("mosaic.fixture.classpath"),
        "-Xplugin=${System.getProperty("mosaic.plugin.jar")}",
        "-P", "plugin:org.buildmosaic.analysis:output=${summary.path}",
        "-P", "plugin:org.buildmosaic.analysis:module=fixture:direct",
        "-P", "plugin:org.buildmosaic.analysis:context=${context.path}",
        "-d", File(root, "classes").path, source.path,
      )
    val failure = compileProtocolFixture(args)
    assertFalse(failure.first, failure.second)
    assertFalse(summary.exists())

    val shards = File(root, "shards").apply { mkdirs() }
    File(shards, "old.json").writeText("old successful shard")
    context.delete()
    val shardArgs =
      args.map {
        if (it == "plugin:org.buildmosaic.analysis:output=${summary.path}") {
          "plugin:org.buildmosaic.analysis:output=${shards.path}"
        } else {
          it
        }
      }.dropLast(1) +
        listOf(
          "-P", "plugin:org.buildmosaic.analysis:mode=shards",
          "-P", "plugin:org.buildmosaic.analysis:sourceRoot=${root.path}", source.path,
        )
    val unreadableContext = compileProtocolFixture(shardArgs)
    assertFalse(unreadableContext.first, unreadableContext.second)
    assertFalse(shards.exists())
  }
}

@Suppress("NestedBlockDepth")
private fun newerRuntime(
  original: File,
  output: File,
  unknown: Boolean,
) {
  JarFile(original).use { input ->
    val manifest = Manifest(input.manifest)
    manifest.mainAttributes.putValue("Implementation-Version", "99.0.0")
    JarOutputStream(output.outputStream(), manifest).use { jar ->
      input.entries().asSequence().filter { it.name != "META-INF/MANIFEST.MF" }.forEach { entry ->
        jar.putNextEntry(JarEntry(entry.name))
        if (entry.name == RUNTIME_DESCRIPTOR_PATH) {
          val requirements =
            if (unknown) "\"mosaic.canvas-analysis/1\",\"mosaic.future/1\"" else "\"mosaic.canvas-analysis/1\""
          jar.write(
            (
              """{"descriptorVersion":1,"module":"org.buildmosaic:mosaic-core",""" +
                """"runtimeVersion":"99.0.0","requires":[$requirements]}"""
            ).toByteArray(),
          )
        } else if (!entry.isDirectory) {
          input.getInputStream(entry).use { it.copyTo(jar) }
        }
        jar.closeEntry()
      }
    }
  }
}
