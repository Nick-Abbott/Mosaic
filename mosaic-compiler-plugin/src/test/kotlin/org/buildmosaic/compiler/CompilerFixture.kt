package org.buildmosaic.compiler

import org.buildmosaic.analysis.ModuleContract
import org.buildmosaic.analysis.SUMMARY_PATH
import org.buildmosaic.analysis.SummaryCodec
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.assertEquals

internal fun compile(
  source: File,
  destination: File,
  dependency: File? = null,
  output: File? = null,
  moduleId: String = "fixture",
) {
  compileFixture(
    listOf(source),
    destination,
    dependency,
    output?.let {
      File(it.parentFile, "summary.json")
    },
    output,
    moduleId,
  )
}

@Suppress("LongParameterList")
private fun compileFixture(
  sources: List<File>,
  destination: File,
  dependency: File?,
  summary: File?,
  output: File?,
  moduleId: String,
) {
  val classpath =
    System.getProperty("mosaic.fixture.classpath") + (
      dependency?.let {
        File.pathSeparator + it.absolutePath
      } ?: ""
    )
  val args =
    mutableListOf(
      "-no-stdlib",
      "-no-reflect",
      "-jvm-target",
      "17",
      "-classpath",
      classpath,
      "-d",
      destination.absolutePath,
    )
  if (summary != null) {
    args += "-Xplugin=${System.getProperty("mosaic.plugin.jar")}"
    args += listOf("-P", "plugin:org.buildmosaic.analysis:output=${summary.absolutePath}")
    args += listOf("-P", "plugin:org.buildmosaic.analysis:module=$moduleId")
    if (output != null) args += listOf("-P", "plugin:org.buildmosaic.analysis:probe=${output.absolutePath}")
  }
  args += sources.map { it.absolutePath }
  // One marker per real compiler invocation keeps fixture cost auditable in JUnit output.
  System.err.println("MOSAIC_COMPILER_INVOCATION")
  if (System.getProperty("mosaic.compat.compiler.classpath") != null) {
    compileIsolated(args, sources, dependency, summary, moduleId)
  } else {
    compileInProcess(args)
  }
}

/** Certification uses the same sources/assertions, with no compiler loaded in the test JVM. */
private fun compileIsolated(
  args: List<String>,
  sources: List<File>,
  dependency: File?,
  summary: File?,
  moduleId: String,
  expectSuccess: Boolean = true,
): Pair<Boolean, String> {
  val caller =
    Throwable().stackTrace.first {
      it.className.contains("Test") && it.className.startsWith("org.buildmosaic.compiler.")
    }
  val input =
    buildString {
      appendLine("${caller.className}.${caller.methodName}:$moduleId:${dependency?.name}")
      sources.forEach {
        appendLine(it.name)
        appendLine(it.readText())
      }
    }
  val key = MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
  val evidence = File(System.getProperty("mosaic.compat.evidence")).apply { mkdirs() }
  val ordinal = evidence.listFiles().orEmpty().count { it.name.startsWith("$key-") && it.extension == "sources" }
  val name = "$key-$ordinal"
  File(evidence, "$name.sources").writeText(input)
  val command =
    listOf(
      System.getProperty("mosaic.compat.java"), "-Xmx768m", "-cp",
      System.getProperty("mosaic.compat.compiler.classpath"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
    ) + args
  File(evidence, "$name.command").writeText(command.joinToString("\n"))
  val log = File(evidence, "$name.log")
  val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).start()
  if (!process.waitFor(120, TimeUnit.SECONDS)) {
    process.destroyForcibly()
    error("Compiler timed out: ${log.absolutePath}")
  }
  val success = process.exitValue() == 0
  if (expectSuccess) assertEquals(0, process.exitValue(), log.readText())
  if (success) summary?.let { it.copyTo(File(evidence, "$name.json")) }
  return success to log.readText()
}

internal fun compileAndExtract(
  sources: List<File>,
  directory: File,
  moduleId: String,
  dependency: File? = null,
): ModuleContract {
  directory.mkdirs()
  val summary = File(directory, "summary.json")
  compileFixture(sources, File(directory, "classes"), dependency, summary, null, moduleId)
  return SummaryCodec.decode(summary.readBytes()).module
}

internal fun jarClasses(
  classes: File,
  output: File,
  summary: File? = null,
) {
  JarOutputStream(output.outputStream()).use { jar ->
    classes.walkTopDown().filter { it.isFile }.forEach { file ->
      jar.putNextEntry(JarEntry(file.relativeTo(classes).invariantSeparatorsPath))
      file.inputStream().use { it.copyTo(jar) }
      jar.closeEntry()
    }
    summary?.let {
      jar.putNextEntry(JarEntry(SUMMARY_PATH))
      it.inputStream().use { input -> input.copyTo(jar) }
      jar.closeEntry()
    }
  }
}

/** Compatibility failures are checked using the same compiler transport as semantic fixtures. */
internal fun compileProtocolFixture(args: List<String>): Pair<Boolean, String> {
  System.err.println("MOSAIC_COMPILER_INVOCATION")
  if (System.getProperty("mosaic.compat.compiler.classpath") == null) return compilerResult(args)
  val output = args.single { it.startsWith("plugin:org.buildmosaic.analysis:output=") }.substringAfter('=')
  val module = args.single { it.startsWith("plugin:org.buildmosaic.analysis:module=") }.substringAfter('=')
  return compileIsolated(args, listOf(File(args.last())), null, File(output), module, expectSuccess = false)
}
