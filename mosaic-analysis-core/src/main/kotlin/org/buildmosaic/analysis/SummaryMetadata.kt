package org.buildmosaic.analysis

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.KeyDeserializer
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.security.MessageDigest

/** Internal provisional JAR resource. A missing or invalid resource is never a complete contract. */
const val SUMMARY_PATH = "META-INF/mosaic-analysis/v1/summary.json"

data class SummaryMetadata(
  val schemaMajor: Int = 1,
  val schemaMinor: Int = 1,
  val toolVersion: String = "prototype-5",
  val kotlinCompilerVersion: String = "2.2.10",
  val moduleId: String,
  val sourceSet: String = "main",
  val complete: Boolean = true,
  val payloadHash: String,
  val module: ModuleContract,
  val limitations: List<String> = emptyList(),
  val binaryLocators: Map<String, String> = emptyMap(),
)

object SummaryCodec {
  private val mapper =
    jacksonObjectMapper()
      .registerModule(
        SimpleModule().apply {
          addKeySerializer(
            ContractParameter::class.java,
            object : JsonSerializer<ContractParameter>() {
              override fun serialize(
                value: ContractParameter,
                gen: JsonGenerator,
                serializers: SerializerProvider,
              ) {
                gen.writeFieldName(listOf(value.owner, value.name, value.kind.name).joinToString("\u001f"))
              }
            },
          )
          addKeyDeserializer(
            ContractParameter::class.java,
            object : KeyDeserializer() {
              override fun deserializeKey(
                key: String,
                ctxt: DeserializationContext,
              ): ContractParameter {
                val parts = key.split("\u001f")
                require(parts.size == 3) { "Malformed contract parameter key" }
                return ContractParameter(parts[0], parts[1], ParameterKind.valueOf(parts[2]))
              }
            },
          )
        },
      ).enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)

  fun encode(
    module: ModuleContract,
    moduleId: String = module.id,
    sourceSet: String = "main",
    limitations: List<String> = emptyList(),
    binaryLocators: Map<String, String> = emptyMap(),
  ): ByteArray {
    require(module.id == moduleId) { "Module identity mismatch" }
    val canonical =
      module.copy(
        canvases = module.canvases.sortedBy { it.id },
        tiles = module.tiles.sortedBy { it.id },
        callables = module.callables.sortedBy { it.id },
      )
    val hash = sha256(mapper.writeValueAsBytes(canonical))
    val summary =
      SummaryMetadata(
        moduleId = moduleId,
        sourceSet = sourceSet,
        payloadHash = hash,
        module = canonical,
        limitations = limitations.distinct().sorted(),
        binaryLocators = binaryLocators.toSortedMap(),
      )
    return mapper.writeValueAsBytes(summary) + '\n'.code.toByte()
  }

  fun decode(bytes: ByteArray): SummaryMetadata {
    val summary =
      try {
        val document = mapper.readTree(bytes)
        require(document != null && document.isObject) { "Missing Mosaic summary object" }
        val required =
          listOf(
            "schemaMajor", "schemaMinor", "toolVersion", "kotlinCompilerVersion", "moduleId", "sourceSet",
            "complete", "payloadHash", "module",
          )
        require(required.all { document.hasNonNull(it) }) { "Incomplete Mosaic summary header" }
        mapper.treeToValue(document, SummaryMetadata::class.java)
      } catch (error: JsonProcessingException) {
        throw IllegalArgumentException("Malformed Mosaic summary", error)
      }
    require(summary.schemaMajor == 1) { "Unsupported Mosaic summary schema major ${summary.schemaMajor}" }
    require(summary.schemaMinor == 1) { "Unsupported Mosaic summary schema minor ${summary.schemaMinor}" }
    require(summary.toolVersion == "prototype-5") { "Unsupported Mosaic extractor version ${summary.toolVersion}" }
    require(summary.complete) { "Partial Mosaic summary cannot be used as complete" }
    require(summary.moduleId == summary.module.id) { "Module identity mismatch" }
    require(summary.sourceSet == "main") { "Unsupported source set identity ${summary.sourceSet}" }
    require(
      summary.kotlinCompilerVersion == "2.2.10",
    ) { "Unsupported Kotlin compiler ${summary.kotlinCompilerVersion}" }
    require(summary.payloadHash == sha256(mapper.writeValueAsBytes(summary.module))) {
      "Mosaic summary payload hash mismatch"
    }
    return summary
  }

  private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
