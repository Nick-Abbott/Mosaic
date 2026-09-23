package org.buildmosaic.compiler

import org.buildmosaic.analysis.AnalysisPolicy
import org.buildmosaic.analysis.AnalysisRequest
import org.buildmosaic.analysis.CanvasExpression
import org.buildmosaic.analysis.Certainty
import org.buildmosaic.analysis.Effect
import org.buildmosaic.analysis.ModuleContract
import org.buildmosaic.analysis.MosaicAnalyzer
import org.buildmosaic.analysis.RootStatus
import org.buildmosaic.analysis.SUMMARY_PATH
import org.buildmosaic.analysis.SelectedRoot
import org.buildmosaic.analysis.SummaryCodec
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MosaicReceiverBoundaryTest {
  @Test
  fun `Mosaic extension helper uses its actual receiver`() {
    val directory = Files.createTempDirectory("mosaic-extension-probe").toFile()
    val source =
      File(directory, "Regression.kt").apply {
        writeText(
          """
          package regression
          import org.buildmosaic.core.*
          import org.buildmosaic.core.injection.*
          class Metrics
          fun Mosaic.readMetrics(): Metrics = source<Metrics>()
          fun parameterRead(receiver: Mosaic): Metrics = receiver.source<Metrics>()
          val ExampleTile = singleTile {
            val other = canvas { }.create()
            other.readMetrics()
          }
          val SuppliedTile = singleTile { canvas { single<Metrics> { Metrics() } }.create().readMetrics() }
          val ParameterTile = singleTile { parameterRead(canvas { }.create()) }
          suspend fun entry() = canvas { single<Metrics> { Metrics() } }.create().compose(ExampleTile)
          suspend fun suppliedEntry() = canvas { single<Metrics> { Metrics() } }.create().compose(SuppliedTile)
          suspend fun parameterEntry() = canvas { single<Metrics> { Metrics() } }.create().compose(ParameterTile)
          """.trimIndent(),
        )
      }
    val module = compileAndExtract(listOf(source), directory, "regression")
    val report = analyze(module)
    assertTrue(
      module.tiles.single { it.id == "regression.ExampleTile" }.effects.any {
        it is Effect.Unknown && it.reason.contains("Mosaic extension receiver")
      },
      module.toString(),
    )
    assertEquals(RootStatus.UNVERIFIED, report.roots.single().status, report.toString())
    assertTrue(
      report.findings.any {
        it.certainty == Certainty.UNVERIFIED && it.reason.contains("Mosaic extension receiver")
      },
    )
    val suppliedReport = analyze(module, target = "regression.suppliedEntry()")
    assertEquals(RootStatus.UNVERIFIED, suppliedReport.roots.single().status, suppliedReport.toString())
    val parameterReport = analyze(module, target = "regression.parameterEntry()")
    assertEquals(RootStatus.UNVERIFIED, parameterReport.roots.single().status, parameterReport.toString())
    assertTrue(
      parameterReport.findings.none {
        it.certainty == Certainty.VERIFIED && it.key?.classId == "regression.Metrics"
      },
    )
  }

  @Test
  fun `member extension remains unknown`() {
    val directory = Files.createTempDirectory("mosaic-member-extension").toFile()
    val source =
      File(directory, "Regression.kt").apply {
        writeText(
          """
          package regression
          import org.buildmosaic.core.*
          import org.buildmosaic.core.injection.*
          class Metrics
          class Reader {
            fun Mosaic.read(): Metrics = source<Metrics>()
            fun invoke(receiver: Mosaic) = receiver.read()
          }
          val ExampleTile = singleTile { Reader().invoke(canvas { }.create()) }
          suspend fun entry() = canvas { single<Metrics> { Metrics() } }.create().compose(ExampleTile)
          """.trimIndent(),
        )
      }
    val module = compileAndExtract(listOf(source), directory, "regression")
    assertTrue(
      module.callables.single { it.id.contains("Reader.invoke") }.effects.any {
        it is Effect.Unknown && it.reason.contains("Mosaic extension receiver")
      },
    )
    assertEquals(RootStatus.UNVERIFIED, analyze(module).roots.single().status)
  }

  @Test
  fun `Canvas Mosaic extension is unknown`() {
    val directory = Files.createTempDirectory("mosaic-canvas-extension").toFile()
    val source =
      File(directory, "Regression.kt").apply {
        writeText(
          """
          package regression
          import org.buildmosaic.core.*
          import org.buildmosaic.core.injection.*
          suspend fun Mosaic.expose(): Canvas = canvas { }
          suspend fun entry(): Canvas = canvas { }.create().expose()
          """.trimIndent(),
        )
      }
    val module = compileAndExtract(listOf(source), directory, "regression")
    val result = module.canvases.single { it.id == "regression.entry()" }.result as CanvasExpression.WithEffects
    assertTrue(result.result is CanvasExpression.Unknown)
    assertTrue((result.result as CanvasExpression.Unknown).reason.contains("Mosaic extension receiver"))
    assertEquals(RootStatus.UNVERIFIED, analyze(module).roots.single().status)
  }
}

class DefaultBoundaryTest {
  @Test
  fun `used default and explicit actual stay distinct`() {
    val directory = Files.createTempDirectory("mosaic-default-probe").toFile()
    val source =
      File(directory, "Regression.kt").apply {
        writeText(
          """
          package regression
          import org.buildmosaic.core.injection.*
          class Metrics
          fun consume(base: Canvas, ignored: Metrics = base.source<Metrics>()) = Unit
          suspend fun entry() { consume(canvas { }) }
          suspend fun explicitEntry() { consume(canvas { }, ignored = Metrics()) }
          suspend fun suppliedEntry() { consume(canvas { single<Metrics> { Metrics() } }) }
          """.trimIndent(),
        )
      }
    val module = compileAndExtract(listOf(source), directory, "regression")
    val report = analyze(module)
    val effects = module.callables.single { it.id == "regression.entry()" }.effects
    assertTrue(effects.any { it is Effect.Unknown && it.reason.contains("default expression") }, module.toString())
    assertTrue(effects.indexOfFirst { it is Effect.ConstructCanvas } < effects.indexOfFirst { it is Effect.Unknown })
    assertTrue(effects.indexOfFirst { it is Effect.Unknown } < effects.indexOfLast { it is Effect.Call })
    assertEquals(RootStatus.UNVERIFIED, report.roots.single().status, report.toString())
    val explicit = analyze(module, target = "regression.explicitEntry()")
    assertEquals(RootStatus.VERIFIED, explicit.roots.single().status, explicit.toString())
    assertTrue(explicit.policyDecision.passed, explicit.toString())
    assertTrue(module.callables.single { it.id == "regression.explicitEntry()" }.effects.none { it is Effect.Unknown })
    val supplied = analyze(module, target = "regression.suppliedEntry()")
    assertEquals(RootStatus.UNVERIFIED, supplied.roots.single().status, supplied.toString())
    assertTrue(supplied.findings.none { it.certainty == Certainty.MISSING })
  }

  @Test
  fun `binary used default is unknown`() {
    val directory = Files.createTempDirectory("mosaic-default-binary").toFile()
    val producer =
      File(directory, "Producer.kt").apply {
        writeText(
          """
          package producer
          import org.buildmosaic.core.injection.*
          class Metrics
          fun consume(base: Canvas, ignored: Metrics = base.source<Metrics>()) = Unit
          """.trimIndent(),
        )
      }
    compileAndExtract(listOf(producer), File(directory, "producer"), "producer")
    val jar = File(directory, "producer.jar")
    jarClasses(File(directory, "producer/classes"), jar, File(directory, "producer/summary.json"))
    val consumer =
      File(directory, "Entry.kt").apply {
        writeText(
          """
          package regression
          import org.buildmosaic.core.injection.*
          import producer.consume
          suspend fun entry() { consume(canvas { }) }
          """.trimIndent(),
        )
      }
    val consumerModule = compileAndExtract(listOf(consumer), File(directory, "consumer"), "regression", jar)
    val producerModule = readJarSummary(jar)
    assertTrue(
      consumerModule.callables.single().effects.any {
        it is Effect.Unknown && it.reason.contains("default expression", ignoreCase = true)
      },
      consumerModule.toString(),
    )
    val withMetadata = analyze(consumerModule, listOf(producerModule))
    val withoutMetadata = analyze(consumerModule)
    assertTrue(
      withMetadata.findings.any {
        it.reason.contains("Default expression is unavailable")
      },
      withMetadata.toString(),
    )
    assertTrue(
      withoutMetadata.findings.any {
        it.reason.contains("Default expression is unavailable")
      },
      withoutMetadata.toString(),
    )
    assertEquals(RootStatus.UNVERIFIED, withMetadata.roots.single().status)
    assertEquals(RootStatus.UNVERIFIED, withoutMetadata.roots.single().status)
  }
}

class InitializationBoundaryTest {
  @Test
  fun `stored property initializer cannot disappear`() {
    val directory = Files.createTempDirectory("mosaic-property-probe").toFile()
    val state =
      File(directory, "State.kt").apply {
        writeText(
          """
          package regression
          import kotlinx.coroutines.runBlocking
          import org.buildmosaic.core.*
          import org.buildmosaic.core.injection.*
          class Metrics
          val startupMetrics = runBlocking { canvas { }.create().source<Metrics>() }
          """.trimIndent(),
        )
      }
    val entry = File(directory, "Entry.kt").apply { writeText("package regression\nfun entry() { startupMetrics }") }
    val module = compileAndExtract(listOf(state, entry), directory, "regression")
    val report = analyze(module)
    assertTrue(
      module.callables.single {
        it.id.contains("get-startupMetrics")
      }.effects.any { it is Effect.Unknown },
      module.toString(),
    )
    assertEquals(RootStatus.UNVERIFIED, report.roots.single().status, report.toString())
    assertTrue(report.findings.any { it.reason.contains("callback") }, report.toString())
  }

  @Test
  fun `backing field read retains initializer boundary`() {
    val directory = Files.createTempDirectory("mosaic-field-probe").toFile()
    val source =
      File(directory, "State.kt").apply {
        writeText(
          """
          package regression
          import kotlinx.coroutines.runBlocking
          import org.buildmosaic.core.*
          import org.buildmosaic.core.injection.*
          class Metrics
          val startupMetrics: Metrics = runBlocking { canvas { }.create().source<Metrics>() }
            get() = field
          fun entry() { startupMetrics }
          """.trimIndent(),
        )
      }
    val module = compileAndExtract(listOf(source), directory, "regression")
    val getter = module.callables.single { it.id.contains("get-startupMetrics") }
    assertTrue(getter.effects.any { it is Effect.Unknown }, getter.toString())
    assertEquals(RootStatus.UNVERIFIED, analyze(module).roots.single().status)
  }

  @Test
  fun `constant and domain constructor are harmless`() {
    val directory = Files.createTempDirectory("mosaic-harmless-init").toFile()
    val source =
      File(directory, "Regression.kt").apply {
        writeText(
          """
          package regression
          const val STARTUP = 7
          class Domain(val name: String)
          val domain = Domain("ok")
          fun entry() { STARTUP; domain }
          """.trimIndent(),
        )
      }
    val module = compileAndExtract(listOf(source), directory, "regression")
    assertTrue(module.callables.single { it.id == "regression.Domain.<init>(kotlin.String)" }.effects.isEmpty())
    assertEquals(RootStatus.VERIFIED, analyze(module).roots.single().status)
  }

  @Test
  fun `binary constructor metadata stays conservative`() {
    val directory = Files.createTempDirectory("mosaic-constructor-probe").toFile()
    val producer =
      File(directory, "Boot.kt").apply {
        writeText(
          """
          package producer
          import kotlinx.coroutines.runBlocking
          import org.buildmosaic.core.*
          import org.buildmosaic.core.injection.*
          class Metrics
          class Boot {
            init { runBlocking { canvas { }.create().source<Metrics>() } }
          }
          """.trimIndent(),
        )
      }
    compileAndExtract(listOf(producer), File(directory, "producer"), "producer")
    val jar = File(directory, "producer.jar")
    jarClasses(File(directory, "producer/classes"), jar, File(directory, "producer/summary.json"))
    val consumer =
      File(directory, "Entry.kt").apply {
        writeText("package regression\nimport producer.Boot\nfun entry() { Boot() }")
      }
    val consumerModule = compileAndExtract(listOf(consumer), File(directory, "consumer"), "regression", jar)
    val producerModule = readJarSummary(jar)
    val withMetadata = analyze(consumerModule, listOf(producerModule))
    val noMetadataJar = File(directory, "producer-without-summary.jar")
    jarClasses(File(directory, "producer/classes"), noMetadataJar)
    val withoutMetadataModule =
      compileAndExtract(listOf(consumer), File(directory, "consumer-without-summary"), "regression", noMetadataJar)
    val withoutMetadata = analyze(withoutMetadataModule)
    assertTrue(
      producerModule.callables.single {
        it.id == "producer.Boot.<init>()"
      }.effects.any { it is Effect.Unknown },
      producerModule.toString(),
    )
    assertTrue(
      consumerModule.callables.single().effects.any { it is Effect.Call && it.target == "producer.Boot.<init>()" },
    )
    assertTrue(
      withMetadata.findings.any {
        it.certainty == Certainty.UNVERIFIED && it.reason.contains("Constructor initialization")
      },
      withMetadata.toString(),
    )
    assertTrue(
      withoutMetadata.findings.any {
        it.certainty == Certainty.UNVERIFIED && it.reason.contains("target producer.Boot.<init>() is missing")
      },
      withoutMetadata.toString(),
    )
  }
}

class StoredFieldBoundaryTest {
  @Test
  fun `JvmField read retains initializer`() {
    val directory = Files.createTempDirectory("mosaic-jvm-field-probe").toFile()
    val source =
      File(directory, "State.kt").apply {
        writeText(
          """
          package regression
          import kotlinx.coroutines.runBlocking
          import org.buildmosaic.core.*
          import org.buildmosaic.core.injection.*
          class Metrics
          @JvmField val startupMetrics = runBlocking { canvas { }.create().source<Metrics>() }
          fun entry() { startupMetrics }
          """.trimIndent(),
        )
      }
    val module = compileAndExtract(listOf(source), directory, "regression")
    val report = analyze(module)
    assertTrue(
      module.callables.single { it.id.contains("get-startupMetrics") }.effects.any { it is Effect.Unknown },
      module.toString(),
    )
    assertEquals(RootStatus.UNVERIFIED, report.roots.single().status, report.toString())
  }
}

private fun analyze(
  module: ModuleContract,
  dependencies: List<ModuleContract> = emptyList(),
  target: String = "regression.entry()",
) = MosaicAnalyzer().analyze(
  AnalysisRequest(module, dependencies, listOf(SelectedRoot("entry", target)), policy = AnalysisPolicy.STRICT),
)

private fun compileAndExtract(
  sources: List<File>,
  directory: File,
  moduleId: String,
  dependency: File? = null,
): ModuleContract {
  directory.mkdirs()
  val classes = File(directory, "classes")
  val summary = File(directory, "summary.json")
  val classpath =
    System.getProperty("mosaic.fixture.classpath") + (
      dependency?.let {
        File.pathSeparator + it.absolutePath
      } ?: ""
    )
  val arguments =
    mutableListOf(
      "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", classpath,
      "-Xplugin=${System.getProperty("mosaic.plugin.jar")}",
      "-P", "plugin:org.buildmosaic.analysis:output=${summary.absolutePath}",
      "-P", "plugin:org.buildmosaic.analysis:module=$moduleId",
      "-d", classes.absolutePath,
    )
  arguments += sources.map { it.absolutePath }
  val error = ByteArrayOutputStream()
  val exit = K2JVMCompiler().exec(PrintStream(error), *arguments.toTypedArray())
  assertEquals(ExitCode.OK, exit, error.toString())
  return SummaryCodec.decode(summary.readBytes()).module
}

private fun readJarSummary(jar: File): ModuleContract =
  JarFile(jar).use { archive ->
    SummaryCodec.decode(archive.getInputStream(archive.getJarEntry(SUMMARY_PATH)).readBytes()).module
  }

private fun jarClasses(
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
