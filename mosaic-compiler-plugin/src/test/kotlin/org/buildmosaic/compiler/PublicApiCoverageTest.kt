@file:Suppress("FunctionMaxLength", "LongMethod", "MaxLineLength", "ktlint:standard:max-line-length")

package org.buildmosaic.compiler

import org.buildmosaic.analysis.AnalysisPolicy
import org.buildmosaic.analysis.AnalysisRequest
import org.buildmosaic.analysis.Certainty
import org.buildmosaic.analysis.MosaicAnalyzer
import org.buildmosaic.analysis.RootStatus
import org.buildmosaic.analysis.SelectedRoot
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One K2 compilation and independently selected public-API roots. */
class PublicApiCoverageTest {
  private companion object {
    val module by lazy {
      val directory = Files.createTempDirectory("mosaic-public-api").toFile()
      val keys = File(directory, "Keys.kt")
      keys.writeText(
        """
        package publicapi
        import org.buildmosaic.core.injection.*
        class Metrics
        val SharedKey = CanvasKey(Metrics::class, "named")
        val EmptyKey = CanvasKey(Metrics::class, "")
        val NullKey = CanvasKey(Metrics::class)
        """.trimIndent(),
      )
      val roots = File(directory, "Roots.kt")
      roots.writeText(
        """
        package publicapi
        import org.buildmosaic.core.*
        import org.buildmosaic.core.injection.*

        val NeedsMetricsTile = singleTile { source<Metrics>("named"); "ok" }
        val NeedsManyTile = multiTile<String, String> { source<Metrics>("named"); emptyMap() }
        val NeedsPerKeyTile = perKeyTile<String, String> { source<Metrics>("named"); "ok" }
        val NeedsChunkTile = chunkedMultiTile<String, String>(2) { source<Metrics>("named"); emptyMap() }
        val DirectTile = Tile { source<Metrics>("named"); "ok" }
        val DirectMultiTile = MultiTile<String, String> { source<Metrics>("named"); emptyMap() }
        val CanvasPropertyTile = singleTile { val c = canvas; c.source<Metrics>(); "ok" }
        val DirectCanvasPropertyTile = singleTile { canvas.source<Metrics>(); "ok" }
        val CurrentMosaicTile = singleTile { source<Metrics>(); "ok" }
        val BothLookupTile = singleTile { source<Metrics>(); canvas.source<Metrics>() }

        suspend fun kclassMissing() { canvas {}.source(Metrics::class, "named") }
        suspend fun kclassAliasMissing() { val type = Metrics::class; canvas {}.source(type, "named") }
        suspend fun keyMissing() { canvas {}.source(CanvasKey(Metrics::class, "named")) }
        suspend fun aliasMissing() { val k = CanvasKey(Metrics::class, "named"); val alias = k; canvas {}.source(alias) }
        suspend fun crossFileMissing() { canvas {}.source(SharedKey) }
        suspend fun optionalKclass() { canvas {}.sourceOr(Metrics::class, "named") }
        suspend fun optionalKey() { canvas {}.sourceOr(CanvasKey(Metrics::class, "named")) }
        suspend fun optionalReified() { canvas {}.sourceOr<Metrics>() }
        suspend fun qualifiedReified() { canvas { single<Metrics>("named") { Metrics() } }.source(Metrics::class, "named") }
        suspend fun qualifiedExplicit() { canvas { single(CanvasKey(Metrics::class, "named")) { Metrics() } }.source(SharedKey) }
        suspend fun keyReuse() { canvas { single(SharedKey) { Metrics() } }.source(SharedKey) }
        suspend fun emptyDistinct() { canvas { single<Metrics>() { Metrics() } }.source(EmptyKey) }
        suspend fun emptyMatched() { canvas { single<Metrics>("") { Metrics() } }.source(EmptyKey) }
        suspend fun nullMatched() { canvas { single<Metrics> { Metrics() } }.source(NullKey) }
        suspend fun nullDistinct() { canvas { single<Metrics>("") { Metrics() } }.source(NullKey) }
        suspend fun mosaicKey() { val m = canvas {}.create(); m.source(SharedKey) }
        suspend fun mosaicOptionalKey() { val m = canvas {}.create(); m.sourceOr(SharedKey) }
        suspend fun mosaicPropertyMissing() { canvas {}.create().compose(CanvasPropertyTile) }
        suspend fun mosaicPropertySupplied() { canvas { single<Metrics> { Metrics() } }.create().compose(CanvasPropertyTile) }
        suspend fun currentMosaicMissing() { canvas {}.create().compose(CurrentMosaicTile) }
        suspend fun currentMosaicSupplied() { canvas { single<Metrics> { Metrics() } }.create().compose(CurrentMosaicTile) }
        suspend fun bothLookups() { canvas { single<Metrics> { Metrics() } }.create().compose(BothLookupTile) }
        suspend fun directPropertyMissing() { canvas {}.create().compose(DirectCanvasPropertyTile) }
        suspend fun paintExplicit() { canvas { single(SharedKey) { Metrics() }; single<String> { paint(SharedKey); "ok" } } }
        suspend fun paintReified() { canvas { single<Metrics>("named") { Metrics() }; single<String> { paint<Metrics>("named"); "ok" } } }
        suspend fun parentNamed() { canvas(parent = canvas { single<Metrics>("named") { Metrics() } }) { single<String> { paint(SharedKey); "ok" } }.source(SharedKey) }
        suspend fun parentPositional() { canvas(canvas { single(SharedKey) { Metrics() } }) { single<String> { paint(SharedKey); "ok" } }.source(SharedKey) }
        suspend fun parentWorkFirst() { canvas(parent = canvas { single<String> { paint(SharedKey); "x" } }) { single(SharedKey) { Metrics() } } }
        suspend fun layerFallback() { val p = canvas { single(SharedKey) { Metrics() } }; val alias = p; alias.withLayer { single<String> { paint(SharedKey); "ok" } }.source(SharedKey) }
        suspend fun composeSync() { canvas {}.create().compose(NeedsMetricsTile) }
        suspend fun composeAsync() { canvas {}.create().composeAsync(NeedsMetricsTile) }
        suspend fun manySingle() { canvas {}.create().compose(NeedsManyTile, "a") }
        suspend fun manySingleAsync() { canvas {}.create().composeAsync(NeedsManyTile, "a") }
        suspend fun manyNonempty() { canvas {}.create().compose(NeedsManyTile, listOf("a")) }
        suspend fun manyNonemptyAsync() { canvas {}.create().composeAsync(NeedsManyTile, listOf("a")) }
        suspend fun manyEmpty() { canvas {}.create().compose(NeedsManyTile, emptyList()) }
        suspend fun manyEmptyAsync() { canvas {}.create().composeAsync(NeedsManyTile, emptyList()) }
        suspend fun perKeySingle() { canvas {}.create().compose(NeedsPerKeyTile, "a") }
        suspend fun chunkSingle() { canvas {}.create().compose(NeedsChunkTile, "a") }
        suspend fun directTile() { canvas {}.create().compose(DirectTile) }
        suspend fun directMultiTile() { canvas {}.create().compose(DirectMultiTile, "a") }
        suspend fun localSingle() { val tile = singleTile { source<Metrics>("named"); "ok" }; canvas {}.create().compose(tile) }
        suspend fun directFresh() { canvas {}.create().compose(singleTile { source<Metrics>("named"); "ok" }) }
        suspend fun localDirectConstructor() { val tile = Tile { source<Metrics>("named"); "ok" }; canvas {}.create().compose(tile) }
        suspend fun localMulti() { val tile = multiTile<String, String> { source<Metrics>("named"); emptyMap() }; canvas {}.create().compose(tile, "a") }
        suspend fun localMultiEmpty() { val tile = multiTile<String, String> { source<Metrics>("named"); emptyMap() }; canvas {}.create().compose(tile, emptyList()) }
        suspend fun localTileAlias() { val tile = singleTile { source<Metrics>("named"); "ok" }; val alias = tile; canvas {}.create().compose(alias) }
        suspend fun localPerKey() { val tile = perKeyTile<String, String> { source<Metrics>("named"); "ok" }; canvas {}.create().compose(tile, "a") }
        suspend fun localChunked() { val tile = chunkedMultiTile<String, String>(2) { source<Metrics>("named"); emptyMap() }; canvas {}.create().compose(tile, "a") }
        suspend fun localChunkedCreationWork() { val tile = chunkedMultiTile<String, String>(canvas {}.source<Int>()) { source<Metrics>("named"); emptyMap() }; canvas {}.create().compose(tile, emptyList()) }
        suspend fun localScalarCapture() { val qualifier = "named"; val tile = singleTile { source<Metrics>(qualifier); "ok" }; canvas {}.create().compose(tile) }
        suspend fun localCanvasCapture() { val captured = canvas {}; val tile = singleTile { captured.source<Metrics>(); "ok" }; canvas {}.create().compose(tile) }
        suspend fun localCapturedMultiEmpty() { val captured = canvas {}; val tile = multiTile<String, String> { captured.source<Metrics>(); emptyMap() }; canvas {}.create().compose(tile, emptyList()) }
        suspend fun twoLocalTilesAsync() { val first = singleTile { source<Metrics>("named"); "a" }; val second = singleTile { source<Metrics>("named"); "b" }; val m = canvas {}.create(); m.composeAsync(first); m.composeAsync(second) }
        suspend fun dynamicType(type: kotlin.reflect.KClass<Metrics>) { canvas {}.source(type) }
        suspend fun dynamicKey(key: CanvasKey<Metrics>) { canvas {}.source(key) }
        suspend fun dynamicOptionalKey(key: CanvasKey<Metrics>) { canvas {}.sourceOr(key) }
        suspend fun dynamicQualifier(q: String) { canvas {}.create().source<Metrics>(q) }
        suspend fun dynamicRegistration(q: String) { canvas { single<Metrics>(q) { Metrics() } }.source<Metrics>() }
        suspend fun dynamicRegistrationKey(key: CanvasKey<Metrics>) { canvas { single(key) { Metrics() } } }
        suspend fun dynamicPaintKey(key: CanvasKey<Metrics>) { canvas { single<String> { paint(key); "ok" } } }
        """.trimIndent(),
      )
      compileAndExtract(listOf(keys, roots), directory, "publicapi")
    }
  }

  private fun report(name: String) =
    MosaicAnalyzer().analyze(
      AnalysisRequest(
        module,
        roots =
          listOf(
            SelectedRoot(
              name,
              "publicapi.${if (name.startsWith("dynamicType")) {
                "dynamicType(kotlin.reflect.KClass)"
              } else if (name in setOf("dynamicKey", "dynamicOptionalKey", "dynamicRegistrationKey", "dynamicPaintKey")) {
                "$name(org.buildmosaic.core.injection.CanvasKey)"
              } else if (name.startsWith("dynamicQualifier") || name.startsWith("dynamicRegistration")) {
                "$name(kotlin.String)"
              } else {
                "$name()"
              }}",
            ),
          ),
        policy = AnalysisPolicy.STRICT,
      ),
    )

  private fun missing(name: String) {
    val report = report(name)
    assertTrue(
      report.findings.any {
        it.certainty == Certainty.MISSING && it.key?.classId == "publicapi.Metrics"
      },
      "$name: $report",
    )
  }

  private fun exactMissing(
    name: String,
    classId: String = "publicapi.Metrics",
  ) {
    val result = report(name)
    assertTrue(
      result.findings.any { it.certainty == Certainty.MISSING && it.key?.classId == classId },
      "$name: $result",
    )
    assertTrue(result.findings.none { it.certainty == Certainty.UNVERIFIED }, "$name: $result")
  }

  private fun verified(name: String) {
    val report = report(name)
    assertEquals(RootStatus.VERIFIED, report.roots.single().status, "$name: $report")
  }

  private fun unknown(name: String) {
    val report = report(name)
    assertTrue(report.findings.any { it.certainty == Certainty.UNVERIFIED }, "$name: $report")
    assertTrue(report.findings.none { it.certainty == Certainty.MISSING }, "$name: $report")
  }

  @Test fun `lookup forms share exact key identity`() {
    listOf(
      "kclassMissing",
      "kclassAliasMissing",
      "keyMissing",
      "aliasMissing",
      "crossFileMissing",
      "emptyDistinct",
      "nullDistinct",
      "mosaicKey",
    ).forEach(::missing)
    listOf("optionalKclass", "optionalKey", "optionalReified", "qualifiedReified", "qualifiedExplicit", "keyReuse", "emptyMatched", "nullMatched", "mosaicOptionalKey").forEach(
      ::verified,
    )
  }

  @Test fun `Mosaic canvas and layer construction preserve ownership`() {
    listOf(
      "mosaicPropertyMissing",
      "directPropertyMissing",
      "currentMosaicMissing",
      "parentWorkFirst",
    ).forEach(::missing)
    listOf(
      "mosaicPropertySupplied",
      "currentMosaicSupplied",
      "paintExplicit",
      "paintReified",
      "parentNamed",
      "parentPositional",
      "layerFallback",
    ).forEach(::verified)
    val both = report("bothLookups").findings.filter { it.key?.classId == "publicapi.Metrics" }
    assertEquals(2, both.size)
    assertEquals(1, both.map { it.bindingSite }.distinct().size)
  }

  @Test fun `compose families preserve execution distinctions`() {
    listOf("composeSync", "composeAsync", "manySingle", "manySingleAsync", "manyNonempty", "manyNonemptyAsync", "perKeySingle", "chunkSingle", "directTile", "directMultiTile").forEach(
      ::missing,
    )
    listOf("manyEmpty", "manyEmptyAsync").forEach(::verified)
  }

  @Test fun `dynamic keys and qualifiers stay named unknown`() {
    listOf(
      "dynamicType",
      "dynamicKey",
      "dynamicOptionalKey",
      "dynamicQualifier",
      "dynamicRegistration",
      "dynamicRegistrationKey",
      "dynamicPaintKey",
    ).forEach(::unknown)
  }

  @Test fun `fresh Tile factories retain deferred bodies and allocation aliases`() {
    listOf(
      "localSingle",
      "directFresh",
      "localDirectConstructor",
      "localMulti",
      "localTileAlias",
      "localPerKey",
      "localChunked",
      "localScalarCapture",
    ).forEach(::exactMissing)
    exactMissing("localChunkedCreationWork", "kotlin.Int")
    verified("localMultiEmpty")
    verified("localCapturedMultiEmpty")
    unknown("localCanvasCapture")
    assertTrue(report("localCanvasCapture").findings.any { it.reason.contains("capture", ignoreCase = true) })
    val separate = report("twoLocalTilesAsync").findings.filter { it.key?.classId == "publicapi.Metrics" }
    assertEquals(2, separate.size)
    assertEquals(2, separate.map { it.dependencyPath.last().label }.toSet().size)
  }
}

class BinaryCanvasKeyExportTest {
  @Test fun `binary immutable key resolves only through producer export`() {
    val directory = Files.createTempDirectory("mosaic-binary-key").toFile()
    val producer = File(directory, "Export.kt")
    producer.writeText(
      """
      package producer
      import org.buildmosaic.core.injection.*
      class Metrics
      val SharedKey = CanvasKey(Metrics::class, "named")
      """.trimIndent(),
    )
    val producerDirectory = File(directory, "producer")
    val producerModule = compileAndExtract(listOf(producer), producerDirectory, "producer")
    val jar = File(directory, "producer.jar")
    jarClasses(File(producerDirectory, "classes"), jar, File(producerDirectory, "summary.json"))
    val consumer = File(directory, "Consumer.kt")
    consumer.writeText(
      """
      package consumer
      import producer.*
      import org.buildmosaic.core.injection.*
      suspend fun entry() {
        val c = canvas {
          single(SharedKey) { Metrics() }
          single<String> { paint(SharedKey); "ok" }
        }
        c.source(SharedKey)
      }
      """.trimIndent(),
    )
    val consumerModule = compileAndExtract(listOf(consumer), File(directory, "consumer"), "consumer", jar)

    fun analyze(dependencies: List<org.buildmosaic.analysis.ModuleContract>) =
      MosaicAnalyzer().analyze(
        AnalysisRequest(
          consumerModule,
          selectedDependencies = dependencies,
          roots = listOf(SelectedRoot("entry", "consumer.entry()")),
          policy = AnalysisPolicy.STRICT,
        ),
      )
    val withExport = analyze(listOf(producerModule))
    assertEquals(RootStatus.VERIFIED, withExport.roots.single().status, withExport.toString())
    val withoutExport = analyze(emptyList())
    assertTrue(
      withoutExport.findings.any {
        it.certainty == Certainty.UNVERIFIED && it.reason.contains("exported stable CanvasKey")
      },
      withoutExport.toString(),
    )
  }
}
