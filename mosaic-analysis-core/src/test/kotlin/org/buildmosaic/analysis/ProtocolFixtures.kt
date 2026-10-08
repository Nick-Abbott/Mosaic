package org.buildmosaic.analysis

internal val fixtureContext = ProductionContext(ProducerIdentity("fixture-analysis", "2.4.20"), emptyList())

internal val fixtureEnvironment = ExtractionEnvironment(fixtureContext, "0".repeat(64), "1".repeat(64))

internal fun encodeFixtureSummary(
  module: ModuleContract,
  moduleId: String = module.id,
  sourceSet: String = "main",
  limitations: List<String> = emptyList(),
  binaryLocators: Map<String, String> = emptyMap(),
): ByteArray = SummaryCodec.encode(module, fixtureContext, moduleId, sourceSet, limitations, binaryLocators)

internal fun fixtureShard(
  sourceId: String,
  module: ModuleContract,
  limitations: List<String> = emptyList(),
  binaryLocators: Map<String, String> = emptyMap(),
): SourceShard = SourceShard(sourceId, module, limitations, binaryLocators, fixtureEnvironment, "0".repeat(64))
