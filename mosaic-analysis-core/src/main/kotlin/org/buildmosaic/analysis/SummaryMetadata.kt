package org.buildmosaic.analysis

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.buildmosaic.analysis.metadata.WireEnvelope
import org.buildmosaic.analysis.metadata.WireLocator
import org.buildmosaic.analysis.metadata.WirePayload
import org.buildmosaic.analysis.metadata.toModel
import org.buildmosaic.analysis.metadata.toWire

/** Executing Kotlin compiler and KGP support is independent of packaged contract readability. */
const val ANALYSIS_KOTLIN_VERSION = "2.4.20"

/** Stable discovery location; the path does not select contract meaning. */
const val SUMMARY_PATH = "META-INF/mosaic-analysis/v1/summary.json"

data class SummaryMetadata(
  val contractVersion: Int,
  val producer: ProducerIdentity,
  val runtimes: List<RuntimeRequirement>,
  val moduleId: String,
  val sourceSet: String,
  val complete: Boolean,
  val integrityHash: String,
  val module: ModuleContract,
  val limitations: List<String>,
  val binaryLocators: Map<String, String>,
)

object SummaryCodec {
  const val MINIMUM_READABLE_CONTRACT_VERSION = 6
  const val CURRENT_CONTRACT_VERSION = 6
  private val json = ProtocolJson.json

  fun encode(
    module: ModuleContract,
    context: ProductionContext,
    moduleId: String = module.id,
    sourceSet: String = "main",
    limitations: List<String> = emptyList(),
    binaryLocators: Map<String, String> = emptyMap(),
  ): ByteArray {
    require(module.id == moduleId) { "Module identity mismatch" }
    require(moduleId.isNotBlank() && sourceSet == "main") { "Unsupported Mosaic module or source-set identity" }
    CompatibilityProtocol.admit(context.runtimes)
    val payload =
      WirePayload(
        module.toWire(),
        limitations.distinct().sorted(),
        binaryLocators.toSortedMap().map { (id, locator) -> WireLocator(id, locator) },
      )
    val envelope =
      WireEnvelope(
        CURRENT_CONTRACT_VERSION,
        context.producer,
        context.runtimes,
        moduleId,
        sourceSet,
        true,
        "",
        payload,
      )
    val encoded = envelope.copy(integrityHash = integrityHash(envelope))
    return (json.encodeToString(encoded) + "\n").toByteArray(Charsets.UTF_8).also { decode(it) }
  }

  fun decode(bytes: ByteArray): SummaryMetadata {
    val element = ProtocolJson.parse(bytes, "summary")
    require(element is JsonObject) { "Missing Mosaic summary object" }
    require("formatVersion" !in element) {
      "Legacy Mosaic metadata format 5 is outside the stable protocol. " +
        "Regenerate dependency summaries with Mosaic Analysis contract 6."
    }
    val version = element["contractVersion"]
    require(version == JsonPrimitive(CURRENT_CONTRACT_VERSION)) {
      "Unsupported Mosaic contract version $version; " +
        "readable range $MINIMUM_READABLE_CONTRACT_VERSION..$CURRENT_CONTRACT_VERSION. " +
        "Regenerate dependency summaries with a compatible Mosaic Analysis version."
    }
    val envelope = ProtocolJson.decode<WireEnvelope>(element)
    require(envelope.complete) { "Partial Mosaic summary cannot be used as complete" }
    require(envelope.moduleId.isNotBlank() && envelope.moduleId == envelope.payload.module.id) {
      "Module identity mismatch"
    }
    require(envelope.sourceSet == "main") { "Unsupported source set identity ${envelope.sourceSet}" }
    ProductionContext(envelope.producer, envelope.runtimes)
    require(envelope.integrityHash == integrityHash(envelope)) { "Mosaic summary integrity hash mismatch" }
    val module = envelope.payload.module.toModel()
    requireUniqueContractOwners(listOf(module))
    val locators = linkedMapOf<String, String>()
    envelope.payload.binaryLocators.forEach {
      require(locators.putIfAbsent(it.id, it.locator) == null) { "Duplicate binary locator ${it.id}" }
    }
    return SummaryMetadata(
      envelope.contractVersion, envelope.producer, envelope.runtimes, envelope.moduleId, envelope.sourceSet,
      envelope.complete, envelope.integrityHash, module, envelope.payload.limitations, locators,
    )
  }

  /** Hash the canonical entire envelope with just the integrity value blanked. */
  private fun integrityHash(envelope: WireEnvelope): String =
    ProtocolJson.hash(json.encodeToString(envelope.copy(integrityHash = "")).toByteArray(Charsets.UTF_8))
}
