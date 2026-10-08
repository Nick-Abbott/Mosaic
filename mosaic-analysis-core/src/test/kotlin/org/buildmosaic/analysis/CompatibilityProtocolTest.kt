package org.buildmosaic.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Suppress("FunctionMaxLength")
class CompatibilityProtocolTest {
  private val runtime = RuntimeRequirement("org.buildmosaic:mosaic-core", "9.0.0", listOf(CANVAS_ANALYSIS_CAPABILITY))
  private val context = ProductionContext(ProducerIdentity("0.7.0-candidate", "2.4.20"), listOf(runtime))
  private val descriptor =
    """{"descriptorVersion":1,"module":"org.buildmosaic:mosaic-core",""" +
      """"runtimeVersion":"9.0.0","requires":["mosaic.canvas-analysis/1"]}"""

  @Test
  fun `new Runtime releases are admitted by capabilities and retain producer provenance`() {
    assertEquals(runtime, CompatibilityProtocol.decodeDescriptor(descriptor.toByteArray()).requirement())
    val summary = SummaryCodec.decode(SummaryCodec.encode(ModuleContract("library"), context))
    assertEquals(6, summary.contractVersion)
    assertEquals(context.producer, summary.producer)
    assertEquals(listOf(runtime), summary.runtimes)
    val olderProducer = context.copy(producer = ProducerIdentity("0.6.0-analysis", "2.2.21"))
    assertEquals(
      olderProducer.producer,
      SummaryCodec.decode(SummaryCodec.encode(ModuleContract("old"), olderProducer)).producer,
    )
  }

  @Test
  fun `unknown required semantics cannot be hidden by selected older Runtime`() {
    val newer = descriptor.replace("mosaic.canvas-analysis/1", "mosaic.canvas-analysis/2")
    val failure =
      assertFailsWith<IllegalArgumentException> { CompatibilityProtocol.decodeDescriptor(newer.toByteArray()) }
    assertTrue(failure.message.orEmpty().contains("Unknown required"))
    val text = SummaryCodec.encode(ModuleContract("library"), context).decodeToString()
    val unknown = withIntegrityHash(text.replace("mosaic.canvas-analysis/1", "mosaic.future/1"))
    assertFailsWith<IllegalArgumentException> { SummaryCodec.decode(unknown.toByteArray()) }
    val selected = runtime.copy(runtimeVersion = "0.7.0")
    val merged = CompatibilityProtocol.merge(listOf(selected), listOf(runtime))
    assertEquals(listOf("0.7.0", "9.0.0"), merged.map { it.runtimeVersion })
    assertEquals(listOf(runtime), SummaryCodec.decode(text.toByteArray()).runtimes)
  }

  @Test
  fun `descriptor versions fields types and requirements fail closed`() {
    val mutations =
      listOf(
        descriptor.replace("\"descriptorVersion\":1", "\"descriptorVersion\":2"),
        descriptor.replace("\"descriptorVersion\":1", "\"descriptorVersion\":\"1\""),
        descriptor.replace("\"requires\":[\"mosaic.canvas-analysis/1\"]", "\"requires\":[]"),
        descriptor.replace("\"requires\":[\"mosaic.canvas-analysis/1\"]", "\"requires\":null"),
        descriptor.replace("\"requires\":[\"mosaic.canvas-analysis/1\"]", "\"requires\":[1]"),
        descriptor.replace(
          "\"requires\":[\"mosaic.canvas-analysis/1\"]",
          "\"requires\":[\"mosaic.canvas-analysis/1\",\"mosaic.canvas-analysis/1\"]",
        ),
        descriptor.replace("mosaic.canvas-analysis/1", "broken"),
        descriptor.replace("\"runtimeVersion\":\"9.0.0\"", "\"runtimeVersion\":9"),
        descriptor.replace("org.buildmosaic:mosaic-core", "org.other:unknown"),
        descriptor.replace("\"descriptorVersion\":1", "\"descriptorVersion\":1,\"descriptorVersion\":1"),
        descriptor.replace("\"module\":", "\"mandatoryFutureField\":true,\"module\":"),
        descriptor.replace("\"runtimeVersion\":\"9.0.0\",", ""),
      )
    mutations.forEach { text ->
      assertFailsWith<IllegalArgumentException>(text) { CompatibilityProtocol.decodeDescriptor(text.toByteArray()) }
    }
    val tracing =
      descriptor.replace(
        "mosaic-core",
        "mosaic-opentelemetry",
      ).replace("[\"mosaic.canvas-analysis/1\"]", "[]")
    assertTrue(CompatibilityProtocol.decodeDescriptor(tracing.toByteArray()).requires.isEmpty())
  }

  @Test
  fun `integrity covers provenance semantic requirements and all correctness headers`() {
    val text = SummaryCodec.encode(ModuleContract("library"), context).decodeToString()
    val mutations =
      listOf(
        text.replace("0.7.0-candidate", "0.8.0-candidate"),
        text.replace("2.4.20", "2.2.21"),
        text.replace("9.0.0", "10.0.0"),
        text.replace("\"runtimes\":[", "\"unknown\":true,\"runtimes\":["),
        text.replace("\"producer\":", "\"missingProducer\":"),
        text.replace("\"runtimes\":", "\"missingRuntimes\":"),
        text.replace("\"contractVersion\":6", "\"contractVersion\":6,\"contractVersion\":6"),
        text.replace("\"complete\":true", "\"complete\":false"),
      )
    mutations.forEach {
        value ->
      assertFailsWith<IllegalArgumentException>(value) { SummaryCodec.decode(value.toByteArray()) }
    }
    assertFailsWith<IllegalArgumentException> {
      CompatibilityProtocol.admit(listOf(runtime, runtime))
    }
    assertFailsWith<IllegalArgumentException> {
      CompatibilityProtocol.merge(
        listOf(runtime),
        listOf(runtime.copy(requires = listOf(CANVAS_ANALYSIS_CAPABILITY, CANVAS_ANALYSIS_CAPABILITY))),
      )
    }
  }

  @Test
  fun `legacy metadata and bounded malformed JSON reject explicitly`() {
    val legacy = """{"formatVersion":5,"semanticsVersion":"analysis-contract-3","toolVersion":"prototype-11"}"""
    val failure = assertFailsWith<IllegalArgumentException> { SummaryCodec.decode(legacy.toByteArray()) }
    assertTrue(failure.message.orEmpty().contains("Regenerate"))
    assertFailsWith<IllegalArgumentException> { SummaryCodec.decode(byteArrayOf(0xc3.toByte(), 0x28)) }
    assertFailsWith<IllegalArgumentException> {
      CompatibilityProtocol.decodeDescriptor(descriptor.replace("9.0.0", "\\uD800").toByteArray())
    }
    assertFailsWith<IllegalArgumentException> { SummaryCodec.decode(ByteArray(ProtocolJson.MAX_BYTES + 1)) }
    assertFailsWith<IllegalArgumentException> { SummaryCodec.decode(("[".repeat(130) + "]".repeat(130)).toByteArray()) }
  }

  @Test
  fun `shards cannot be reused across producer or selected Runtime changes`() {
    val environment = ExtractionEnvironment(context, "0".repeat(64), "1".repeat(64))
    val shard = SourceShard("File.kt", ModuleContract("library"), context = environment, sourceHash = "0".repeat(64))
    assertEquals(shard, SourceShardCodec.decode(SourceShardCodec.encode(shard)))
    val environments =
      listOf(
        context.copy(producer = ProducerIdentity("other-analysis", "2.4.20")),
        context.copy(producer = ProducerIdentity("0.7.0-candidate", "2.4.21")),
        context.copy(runtimes = listOf(runtime.copy(runtimeVersion = "10.0.0"))),
      )
    environments.forEach { changed ->
      assertFailsWith<IllegalArgumentException> {
        SourceShardCodec.assemble("library", listOf(shard), environment.copy(production = changed))
      }
    }
    assertFailsWith<IllegalArgumentException> {
      SourceShardCodec.assemble("library", listOf(shard), environment.copy(analysisArtifactHash = "2".repeat(64)))
    }
    assertFailsWith<IllegalArgumentException> {
      SourceShardCodec.assemble("library", listOf(shard), environment.copy(compilerArtifactHash = "3".repeat(64)))
    }
    val corrupt = SourceShardCodec.encode(shard).decodeToString().replace("\"id\":\"library\"", "\"id\":\"other\"")
    assertFailsWith<IllegalArgumentException> { SourceShardCodec.decode(corrupt.toByteArray()) }
    assertTrue(SummaryCodec.decode(SourceShardCodec.assemble("empty", emptyList(), environment)).module.tiles.isEmpty())
  }
}
