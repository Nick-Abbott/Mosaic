package org.buildmosaic.analysis

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.buildmosaic.analysis.metadata.WireLocator
import org.buildmosaic.analysis.metadata.WirePayload
import org.buildmosaic.analysis.metadata.toModel
import org.buildmosaic.analysis.metadata.toWire

/** Internal compiler output. Shards are never packaged or accepted as dependency metadata. */
data class SourceShard(
  val sourceId: String,
  val module: ModuleContract,
  val limitations: List<String> = emptyList(),
  val binaryLocators: Map<String, String> = emptyMap(),
)

@Serializable
private data class ShardEnvelope(
  val shardVersion: Int,
  val sourceId: String,
  val payload: WirePayload,
)

object SourceShardCodec {
  private val json =
    Json {
      classDiscriminator = "kind"
      encodeDefaults = true
      explicitNulls = true
    }

  fun encode(shard: SourceShard): ByteArray {
    checkSourceId(shard.sourceId)
    val payload =
      WirePayload(
        shard.module.toWire(),
        shard.limitations.distinct().sorted(),
        shard.binaryLocators.toSortedMap().map { (id, locator) -> WireLocator(id, locator) },
      )
    return (json.encodeToString(ShardEnvelope(1, shard.sourceId, payload)) + "\n").toByteArray(Charsets.UTF_8)
  }

  fun decode(bytes: ByteArray): SourceShard {
    val raw = bytes.toString(Charsets.UTF_8)
    require(raw.toByteArray(Charsets.UTF_8).contentEquals(bytes)) { "Malformed Mosaic shard UTF-8" }
    val envelope = json.decodeFromString<ShardEnvelope>(raw)
    require(envelope.shardVersion == 1) { "Unsupported Mosaic shard version ${envelope.shardVersion}" }
    checkSourceId(envelope.sourceId)
    val locators = linkedMapOf<String, String>()
    envelope.payload.binaryLocators.forEach {
      require(locators.putIfAbsent(it.id, it.locator) == null) { "Duplicate Mosaic shard locator ${it.id}" }
    }
    return SourceShard(envelope.sourceId, envelope.payload.module.toModel(), envelope.payload.limitations, locators)
  }

  fun assemble(
    moduleId: String,
    shards: Collection<SourceShard>,
  ): ByteArray {
    val ids = mutableSetOf<String>()
    val canvases = mutableListOf<CanvasContract>()
    val tiles = mutableListOf<TileContract>()
    val callables = mutableListOf<CallableContract>()
    val overrides = mutableListOf<ResolvedOverride>()
    val keys = mutableListOf<KeyContract>()
    val limitations = mutableListOf<String>()
    val locators = linkedMapOf<String, String>()
    val sources = mutableSetOf<String>()

    fun owner(
      kind: String,
      id: String,
    ) {
      require(ids.add("$kind:$id")) { "Duplicate local Mosaic $kind owner $id" }
    }
    shards.sortedBy { it.sourceId }.forEach { shard ->
      require(sources.add(shard.sourceId)) { "Duplicate Mosaic source shard ${shard.sourceId}" }
      require(shard.module.id == moduleId) { "Mosaic shard module mismatch in ${shard.sourceId}" }
      shard.module.canvases.forEach {
        owner("canvas", it.id)
        canvases += it
      }
      shard.module.tiles.forEach {
        owner("tile", it.id)
        tiles += it
      }
      shard.module.callables.forEach {
        owner("callable", it.id)
        callables += it
      }
      shard.module.overrides.forEach {
        owner("override", "${it.receiverType}:${it.baseId}:${it.implementationId}")
        overrides += it
      }
      shard.module.keys.forEach {
        owner("key", it.id)
        keys += it
      }
      limitations += shard.limitations
      shard.binaryLocators.forEach { (id, locator) ->
        require(locators.putIfAbsent(id, locator) == null) { "Duplicate local Mosaic binary locator $id" }
      }
    }
    return SummaryCodec.encode(
      ModuleContract(moduleId, canvases, tiles, callables, overrides, keys),
      limitations = limitations.distinct().sorted(),
      binaryLocators = locators,
    )
  }

  private fun checkSourceId(id: String) {
    require(id.isNotBlank() && !id.startsWith('/') && '\\' !in id && id.split('/').none { it == ".." || it == "." }) {
      "Invalid Mosaic shard source identity $id"
    }
  }
}
