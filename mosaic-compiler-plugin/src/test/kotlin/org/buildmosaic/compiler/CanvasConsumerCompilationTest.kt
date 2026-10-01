package org.buildmosaic.compiler

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasConsumerCompilationTest {
  @Test
  fun `Kotlin rejects custom Canvas implementations`() {
    val directory = Files.createTempDirectory("mosaic-closed-canvas").toFile()
    val source = File(directory, "Consumer.kt")
    source.writeText(
      """
      package org.buildmosaic.core.injection
      fun construct() = Canvas(emptyMap(), emptyList(), null, error("hidden configuration"))
      abstract class CustomCanvas : Canvas
      class DelegatingCanvas(parent: Canvas) : Canvas by parent
      val anonymous = object : Canvas {
        override fun <T : Any> sourceOr(key: CanvasKey<T>): T? = null
      }
      """.trimIndent(),
    )
    val diagnostics = ByteArrayOutputStream()
    val exit =
      K2JVMCompiler().exec(
        PrintStream(diagnostics),
        "-no-stdlib", "-no-reflect", "-jvm-target", "17",
        "-classpath", System.getProperty("mosaic.fixture.classpath"),
        "-d", File(directory, "classes").absolutePath, source.absolutePath,
      )
    assertEquals(ExitCode.COMPILATION_ERROR, exit, diagnostics.toString())
    val errors = diagnostics.toString()
    assertTrue(Regex("this type is final", RegexOption.IGNORE_CASE).findAll(errors).count() >= 3, errors)
    assertTrue(errors.contains("internal", ignoreCase = true), errors)
    assertTrue(errors.contains("delegation is supported only for interfaces", ignoreCase = true), errors)
  }

  @Test
  fun `Java consumers cannot implement Canvas`() {
    val directory = Files.createTempDirectory("mosaic-java-canvas").toFile()
    val source = File(directory, "CustomCanvas.java")
    source.writeText(
      """
      import org.buildmosaic.core.injection.Canvas;
      public abstract class CustomCanvas extends Canvas {}
      """.trimIndent(),
    )
    val diagnostics = ByteArrayOutputStream()
    val exit =
      ToolProvider.getSystemJavaCompiler().run(
        null, null, diagnostics,
        "--release", "17", "-classpath", System.getProperty("mosaic.fixture.classpath"),
        "-d", directory.absolutePath, source.absolutePath,
      )
    assertEquals(1, exit, diagnostics.toString())
    assertTrue(diagnostics.toString().contains("final", ignoreCase = true), diagnostics.toString())
  }

  @Test
  fun `integration API compiles without opt in`() {
    val directory = Files.createTempDirectory("mosaic-integration-api").toFile()
    val source = File(directory, "Integration.kt")
    source.writeText(
      """
      package integration
      import org.buildmosaic.core.*
      import org.buildmosaic.core.injection.*
      import org.buildmosaic.core.instrumentation.*
      class Provider : MosaicInstrumentation {
        override fun captureCaller(execution: MosaicInstrumentation.ExecutionIdentity?) = null
        override fun startSingle(name: String?, caller: MosaicInstrumentation.CallerContext?) = null
        override fun createBatch() = null
        override fun onCallbackFailure(failure: Throwable) = Unit
      }
      class Execution : MosaicInstrumentation.Execution {
        override val identity = object : MosaicInstrumentation.ExecutionIdentity {}
        override fun dependency(producer: ProducerReference) { producer.subscribe { } }
        override fun complete(completion: ExecutionCompletion) { completion.outcome; completion.errorType }
      }
      fun CanvasBuilder.integration() { installInstrumentation { Provider() } }
      suspend fun consumer(): String {
        val application: Canvas = canvas { integration(); single<String> { "service" } }
        val request: Canvas = application.withLayer { single<Int> { 7 } }
        val mosaic: Mosaic = request.create()
        return mosaic.compose(singleTile { source<String>() + source<Int>() })
      }
      """.trimIndent(),
    )
    compile(source, File(directory, "classes"))
  }
}
