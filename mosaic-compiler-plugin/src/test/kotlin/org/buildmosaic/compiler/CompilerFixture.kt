package org.buildmosaic.compiler

import org.buildmosaic.analysis.ModuleContract
import org.buildmosaic.analysis.SUMMARY_PATH
import org.buildmosaic.analysis.SummaryCodec
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
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
  val error = ByteArrayOutputStream()
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
  val exit =
    K2JVMCompiler().also {
      System.err.println("MOSAIC_COMPILER_INVOCATION")
    }.exec(PrintStream(error), *args.toTypedArray())
  assertEquals(ExitCode.OK, exit, error.toString())
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
