package org.buildmosaic.compiler

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasConsumerCompilationTest {
  @Test
  fun `Canvas typed layers compile with use`() {
    val directory = Files.createTempDirectory("mosaic-canvas-consumer").toFile()
    try {
      val source = File(directory, "Consumer.kt")
      source.writeText(
        """
        import org.buildmosaic.core.*
        import org.buildmosaic.core.injection.*
        class Service(val value: String)
        val RequestKey = CanvasKey(String::class, "request")
        suspend fun consumer(): String {
          val applicationCanvas: Canvas = canvas {
            single<String> { "service" }
            single<Service> { Service(paint<String>()) }
          }
          return applicationCanvas.use { application ->
            val requestCanvas: Canvas = application.withLayer { single(RequestKey) { "request" } }
            requestCanvas.use { request ->
              request.create().compose(singleTile { source<Service>().value + source(RequestKey) })
            }
          }
        }
        """.trimIndent(),
      )
      compile(source, File(directory, "classes"))
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun `Canvas rejects external construction`() {
    val directory = Files.createTempDirectory("mosaic-canvas-construction").toFile()
    try {
      val source = File(directory, "Consumer.kt")
      source.writeText(
        """
        import org.buildmosaic.core.injection.*
        fun direct() = Canvas(emptyMap(), emptyList())
        abstract class Custom : Canvas()
        class Delegating(parent: Canvas) : Canvas by parent
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
      val errors = diagnostics.toString()
      assertEquals(ExitCode.COMPILATION_ERROR, exit, errors)
      assertTrue(errors.contains("internal", ignoreCase = true), errors)
      assertTrue(errors.contains("final", ignoreCase = true), errors)
      assertTrue(errors.contains("delegation is supported only for interfaces", ignoreCase = true), errors)
    } finally {
      directory.deleteRecursively()
    }
  }
}
