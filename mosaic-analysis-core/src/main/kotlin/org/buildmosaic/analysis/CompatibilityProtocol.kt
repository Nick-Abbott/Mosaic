package org.buildmosaic.analysis

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

const val RUNTIME_DESCRIPTOR_PATH = "META-INF/mosaic/runtime-compatibility.json"

/** Identities are diagnostic provenance; admission compares immutable semantic capabilities. */
@Serializable
data class RuntimeRequirement(val module: String, val runtimeVersion: String, val requires: List<String>)

@Serializable
data class RuntimeDescriptor(
  val descriptorVersion: Int,
  val module: String,
  val runtimeVersion: String,
  val requires: List<String>,
) {
  fun requirement(): RuntimeRequirement = RuntimeRequirement(module, runtimeVersion, requires)
}

@Serializable
data class ProducerIdentity(val analysisVersion: String, val compilerVersion: String) {
  init {
    require(validIdentity(analysisVersion) && validIdentity(compilerVersion)) { "Invalid Mosaic producer provenance" }
  }
}

/** Explicit production input to the pure writer. Shards retain this exact extraction environment. */
@Serializable
data class ProductionContext(val producer: ProducerIdentity, val runtimes: List<RuntimeRequirement>) {
  init {
    CompatibilityProtocol.admit(runtimes)
    require(runtimes == CompatibilityProtocol.merge(runtimes)) { "Noncanonical Mosaic Runtime requirements" }
  }
}

private const val MAX_IDENTITY_LENGTH = 256

internal fun validIdentity(value: String): Boolean =
  value.isNotBlank() && value.length <= MAX_IDENTITY_LENGTH && value.none { it.isWhitespace() || it.isISOControl() }

/** The single owner of Runtime semantic admission for descriptors, summaries and extraction. */
object CompatibilityProtocol {
  val runtimeModules: Set<String> =
    setOf("org.buildmosaic:mosaic-core", "org.buildmosaic:mosaic-test", "org.buildmosaic:mosaic-opentelemetry")

  fun admit(requirements: Collection<RuntimeRequirement>) {
    val identities = mutableSetOf<Pair<String, String>>()
    requirements.forEach { runtime ->
      require(runtime.module in runtimeModules) { "Unsupported Mosaic Runtime module ${runtime.module}" }
      require(validIdentity(runtime.runtimeVersion)) { "Invalid Mosaic Runtime version" }
      require(identities.add(runtime.module to runtime.runtimeVersion)) {
        "Duplicate or conflicting Mosaic Runtime identity ${runtime.module}:${runtime.runtimeVersion}"
      }
      require(runtime.requires.size == runtime.requires.distinct().size) { "Duplicate Mosaic Runtime requirement" }
      runtime.requires.forEach { capability ->
        require(Regex("[a-z][a-z0-9.-]*/[1-9][0-9]*").matches(capability)) {
          "Malformed Mosaic Runtime requirement $capability"
        }
        require(capability == CANVAS_ANALYSIS_CAPABILITY) {
          "Unknown required Mosaic Runtime capability $capability in ${runtime.module}:${runtime.runtimeVersion}. " +
            "Upgrade Mosaic Analysis to understand this capability or select a compatible Runtime."
        }
      }
      require(runtime.module != "org.buildmosaic:mosaic-core" || CANVAS_ANALYSIS_CAPABILITY in runtime.requires) {
        "Missing Canvas analysis requirement for ${runtime.module}:${runtime.runtimeVersion}"
      }
    }
  }

  fun merge(vararg groups: Collection<RuntimeRequirement>): List<RuntimeRequirement> {
    val byIdentity = linkedMapOf<Pair<String, String>, RuntimeRequirement>()
    groups.forEach { group ->
      admit(group)
      group.forEach { runtime ->
        val canonical = runtime.copy(requires = runtime.requires.sorted())
        val previous = byIdentity.putIfAbsent(runtime.module to runtime.runtimeVersion, canonical)
        require(
          previous == null || previous == canonical,
        ) { "Conflicting Mosaic Runtime requirements for ${runtime.module}" }
      }
    }
    return byIdentity.values.sortedWith(compareBy({ it.module }, { it.runtimeVersion }))
  }

  fun decodeDescriptor(bytes: ByteArray): RuntimeDescriptor {
    val element = ProtocolJson.parse(bytes, "Runtime descriptor")
    require(element is JsonObject) { "Missing Mosaic Runtime descriptor object" }
    require(element["descriptorVersion"] == JsonPrimitive(1)) { "Unsupported Mosaic Runtime descriptor version" }
    return ProtocolJson.decode<RuntimeDescriptor>(element).also { admit(listOf(it.requirement())) }
  }

  fun encodeContext(context: ProductionContext): ByteArray =
    (ProtocolJson.json.encodeToString(context) + "\n").toByteArray(Charsets.UTF_8)

  fun decodeContext(bytes: ByteArray): ProductionContext =
    ProtocolJson.decode(
      ProtocolJson.parse(bytes, "production context"),
    )
}
