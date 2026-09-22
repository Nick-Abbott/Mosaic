@file:Suppress("LargeClass", "LongMethod", "FunctionMaxLength", "MaxLineLength", "ktlint:standard:max-line-length")

package org.buildmosaic.compiler

import org.buildmosaic.analysis.AnalysisPolicy
import org.buildmosaic.analysis.AnalysisRequest
import org.buildmosaic.analysis.CanvasExpression
import org.buildmosaic.analysis.Certainty
import org.buildmosaic.analysis.DiscoveryKind
import org.buildmosaic.analysis.Effect
import org.buildmosaic.analysis.LookupKind
import org.buildmosaic.analysis.MosaicAnalyzer
import org.buildmosaic.analysis.SelectedRoot
import org.buildmosaic.analysis.SummaryCodec
import org.buildmosaic.analysis.TileReference
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IrShapeTest {
  @Test
  fun `unchanged binary caller retains external const literal after provider rebuild`() {
    val directory = Files.createTempDirectory("mosaic-const-binary").toFile()
    val producer = File(directory, "Producer.kt")

    fun producerSource(value: String) =
      """
      package consts
      import org.buildmosaic.core.injection.*
      const val QUALIFIER = "$value"
      class Metrics
      suspend fun base(): Canvas = canvas { single<Metrics>(QUALIFIER) { Metrics() } }
      """.trimIndent()
    producer.writeText(producerSource("old"))
    val firstClasses = File(directory, "first-classes")
    val firstFacts = File(directory, "first/facts.txt")
    compile(producer, firstClasses, output = firstFacts, moduleId = "consts")
    val firstJar = File(directory, "first.jar")
    jarClasses(firstClasses, firstJar)

    val caller = File(directory, "Caller.kt")
    caller.writeText(
      """
      package consumer
      import org.buildmosaic.core.*
      import org.buildmosaic.core.injection.*
      import consts.*
      val CapturedTile = singleTile { source<Metrics>(QUALIFIER) }
      suspend fun entry(): Metrics = base().create().compose(CapturedTile)
      """.trimIndent(),
    )
    val callerFacts = File(directory, "caller/facts.txt")
    compile(caller, File(directory, "caller-classes"), firstJar, callerFacts, "consumer")
    val callerBytes = File(callerFacts.parentFile, "summary.json").readBytes()
    val callerSummary = SummaryCodec.decode(callerBytes)
    assertTrue(callerSummary.limitations.any { it.contains("external const origin") })
    assertTrue(callerBytes.decodeToString().contains("\"qualifier\":\"old\""))

    producer.writeText(producerSource("new"))
    val secondFacts = File(directory, "second/facts.txt")
    compile(producer, File(directory, "second-classes"), output = secondFacts, moduleId = "consts")
    val secondSummary = SummaryCodec.decode(File(secondFacts.parentFile, "summary.json").readBytes())
    assertTrue(
      (secondSummary.module.canvases.single().result as CanvasExpression.Layer).bindings.single().key.toString().contains(
        "new",
      ),
    )
    val report =
      MosaicAnalyzer().analyze(
        AnalysisRequest(
          callerSummary.module,
          listOf(secondSummary.module),
          listOf(SelectedRoot("consumer.entry()", "consumer.entry()")),
          policy = AnalysisPolicy.STRICT,
        ),
      )
    assertTrue(report.findings.any { it.certainty == Certainty.MISSING && it.key?.qualifier == "old" })
    assertTrue(callerBytes.contentEquals(File(callerFacts.parentFile, "summary.json").readBytes()))
  }

  @Test
  fun `external const is substituted but inline helper body is absent before IR lowering`() {
    val directory = Files.createTempDirectory("mosaic-ir-binary").toFile()
    val producer = File(directory, "Producer.kt")
    producer.writeText(
      """
      package producer
      import org.buildmosaic.core.Mosaic
      import org.buildmosaic.core.source
      const val QUALIFIER = "old"
      class Metrics
      inline fun Mosaic.helper(): Metrics = source<Metrics>()
      """.trimIndent(),
    )
    val producerClasses = File(directory, "producer-classes")
    compile(producer, producerClasses)
    val producerJar = File(directory, "producer.jar")
    jarClasses(producerClasses, producerJar)
    val caller = File(directory, "Caller.kt")
    caller.writeText(
      """
      package consumer
      import org.buildmosaic.core.*
      import producer.*
      val Tile = singleTile { source<Metrics>(QUALIFIER); helper() }
      """.trimIndent(),
    )
    val output = File(directory, "caller-facts.txt")
    compile(caller, File(directory, "caller-classes"), producerJar, output)
    val facts = output.readLines()
    assertTrue(facts.any { it.startsWith("CONST|old|") })
    assertTrue(facts.any { it.startsWith("CALL|producer.helper|") })
    assertTrue(facts.none { it.startsWith("FIELD|producer.QUALIFIER|") })
    val summary = SummaryCodec.decode(File(output.parentFile, "summary.json").readBytes())
    assertTrue(summary.module.tiles.single().effects.any { it is org.buildmosaic.analysis.Effect.Unknown })
    // The unresolved external inline body cannot be treated as an empty Mosaic effect.
    assertTrue(
      facts.none {
        it.startsWith("CALL|org.buildmosaic.core.source|") && it.contains("producer.Metrics") && it.contains("helper")
      },
    )
  }

  @Test
  fun `K2 IR exposes the required Mosaic DSL symbols`() {
    val directory = Files.createTempDirectory("mosaic-ir-shape").toFile()
    val source = File(directory, "Fixture.kt")
    source.writeText(
      """
      package fixture
      import org.buildmosaic.core.*
      import org.buildmosaic.core.injection.*

      class GlobalContext
      class Metrics
      class Service(val metrics: Metrics)
      fun overloaded(value: String): String = value
      fun overloaded(value: Int): Int = value
      const val QUALIFIER = "qualified"
      val MetricsTile = singleTile { source<Metrics>() }
      val OtherTile = perKeyTile<String, String> { sourceOr<Metrics>(); it }
      val BatchTile = multiTile<String, String> { keys -> keys.associateWith { source<GlobalContext>(); it } }
      val ConditionalTile = singleTile { if (System.nanoTime() > 0) source<Metrics>() else null }
      val ExplicitKeyTile = singleTile { source(CanvasKey(Metrics::class, "dynamic")) }
      suspend fun platformCanvas(): Canvas = canvas { single<GlobalContext> { GlobalContext() }; single<Metrics>(QUALIFIER) { Metrics() } }
      suspend fun layer(parent: Canvas): Canvas = parent.withLayer { single<Service> { Service(paint<Metrics>()) } }
      suspend fun entry() {
        val base = platformCanvas()
        val aliased = base
        val tile = MetricsTile
        aliased.create().compose(tile)
        aliased.create().composeAsync(OtherTile, "one")
        aliased.create().composeAsync(BatchTile, listOf("one"))
      }
      abstract class PlatformComponent {
        suspend fun handle(id: String): String = respond(platformCanvas(), id)
        protected abstract suspend fun respond(base: Canvas, id: String): String
      }
      class ApplicationComponent : PlatformComponent() {
        override suspend fun respond(base: Canvas, id: String): String { layer(base).create().compose(MetricsTile); return id }
      }
      """.trimIndent(),
    )
    val output = File(directory, "facts.txt")
    val error = ByteArrayOutputStream()
    val exit =
      K2JVMCompiler().exec(
        PrintStream(error),
        "-no-stdlib",
        "-no-reflect",
        "-jvm-target", "17",
        "-classpath", System.getProperty("mosaic.fixture.classpath"),
        "-Xplugin=${System.getProperty("mosaic.plugin.jar")}",
        "-P", "plugin:org.buildmosaic.analysis:output=${File(directory, "summary.json").absolutePath}",
        "-P", "plugin:org.buildmosaic.analysis:module=fixture",
        "-P", "plugin:org.buildmosaic.analysis:probe=${output.absolutePath}",
        "-d", File(directory, "classes").absolutePath,
        source.absolutePath,
      )
    assertEquals(ExitCode.OK, exit, error.toString())
    val facts = output.readLines()
    assertTrue(facts.any { it.startsWith("CALL|org.buildmosaic.core.singleTile|") }, facts.joinToString("\n"))
    assertTrue(facts.any { it.startsWith("CALL|org.buildmosaic.core.injection.canvas|") })
    assertTrue(facts.any { it.startsWith("CALL|org.buildmosaic.core.injection.Canvas.withLayer|") })
    assertTrue(facts.any { it.contains("platformCanvas") })
    assertTrue(facts.any { it.contains("composeAsync") })
    assertTrue(facts.any { it.contains("respond") })
    assertTrue(facts.any { it.startsWith("CONST|qualified|") })
    val summary = SummaryCodec.decode(File(directory, "summary.json").readBytes())
    val tiles = summary.module.tiles.associateBy { it.id }
    assertTrue(
      tiles.getValue("fixture.MetricsTile").effects.any { it is Effect.Lookup && it.kind == LookupKind.REQUIRED },
    )
    assertTrue(
      tiles.getValue("fixture.OtherTile").effects.any { it is Effect.Lookup && it.kind == LookupKind.OPTIONAL },
    )
    assertTrue(tiles.getValue("fixture.BatchTile").effects.any { it is Effect.Unknown })
    assertTrue(
      tiles.getValue("fixture.ExplicitKeyTile").effects.any {
        it is Effect.Lookup && it.key is org.buildmosaic.analysis.Fact.Unknown
      },
    )
    val platform = summary.module.canvases.single { it.id == "fixture.platformCanvas()" }.result as CanvasExpression.Layer
    assertTrue(platform.bindings.any { it.key.toString().contains("qualifier=qualified") })
    val layer = summary.module.canvases.single { it.id.startsWith("fixture.layer(") }.result as CanvasExpression.Layer
    assertTrue(layer.bindings.single().constructorEffects.any { it is Effect.Lookup && it.kind == LookupKind.PAINT })
    val entryEffects = summary.module.callables.single { it.id == "fixture.entry()" }.effects
    assertTrue(
      entryEffects.any {
        it is Effect.Compose && it.discovery == DiscoveryKind.COMPOSE && it.tile == TileReference.Stable("fixture.MetricsTile") && it.canvas is CanvasExpression.Alias
      },
    )
    assertEquals(
      2,
      entryEffects.filterIsInstance<Effect.Compose>().count { it.discovery == DiscoveryKind.COMPOSE_ASYNC },
    )
    assertTrue(
      summary.module.overrides.any {
        it.receiverType == "fixture.ApplicationComponent" && it.baseId.startsWith("fixture.PlatformComponent.respond(")
      },
    )
    assertTrue(
      summary.module.tiles.single {
        it.id == "fixture.ConditionalTile"
      }.effects.any { it is org.buildmosaic.analysis.Effect.Unknown },
    )
    assertTrue(
      summary.binaryLocators.getValue(
        "fixture.platformCanvas()",
      ).contains("#platformCanvas(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;"),
    )
    assertTrue(
      summary.binaryLocators.keys.containsAll(
        listOf("fixture.overloaded(kotlin.String)", "fixture.overloaded(kotlin.Int)"),
      ),
    )
    assertTrue(
      summary.binaryLocators.getValue(
        "fixture.PlatformComponent.handle(kotlin.String)",
      ).contains("#handle(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;"),
    )
  }

  private fun compile(
    source: File,
    destination: File,
    dependency: File? = null,
    output: File? = null,
    moduleId: String = "fixture",
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
    if (output != null) {
      args += "-Xplugin=${System.getProperty("mosaic.plugin.jar")}"
      args += listOf("-P", "plugin:org.buildmosaic.analysis:output=${File(output.parentFile, "summary.json").absolutePath}")
      args += listOf("-P", "plugin:org.buildmosaic.analysis:module=$moduleId")
      args += listOf("-P", "plugin:org.buildmosaic.analysis:probe=${output.absolutePath}")
    }
    args += source.absolutePath
    val exit = K2JVMCompiler().exec(PrintStream(error), *args.toTypedArray())
    assertEquals(ExitCode.OK, exit, error.toString())
  }

  private fun jarClasses(
    classes: File,
    output: File,
  ) {
    JarOutputStream(output.outputStream()).use { jar ->
      classes.walkTopDown().filter { it.isFile }.forEach { file ->
        jar.putNextEntry(JarEntry(file.relativeTo(classes).invariantSeparatorsPath))
        file.inputStream().use { it.copyTo(jar) }
        jar.closeEntry()
      }
    }
  }
}
