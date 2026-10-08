package org.buildmosaic.analysis

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.buildmosaic.analysis.metadata.WireLocator
import org.buildmosaic.analysis.metadata.WirePayload
import org.buildmosaic.analysis.metadata.toModel
import org.buildmosaic.analysis.metadata.toWire
import java.io.File
import java.nio.file.Files

/** One path convention for compiler output and Gradle's current-source inputs. */
object SourceShardPaths {
  private const val SUFFIX = ".shard.json"

  fun sourceId(
    sourceRoot: File,
    source: File,
  ): String {
    require(!Files.isSymbolicLink(sourceRoot.toPath())) { "Mosaic source root must not be a symbolic link" }
    val root = sourceRoot.canonicalFile.toPath()
    val path = source.canonicalFile.toPath()
    require(path.startsWith(root)) { "Mosaic source is outside supported root: $path" }
    return root.relativize(path).toString().replace(File.separatorChar, '/').also(::checkSourceId)
  }

  fun shardFile(
    shardRoot: File,
    sourceId: String,
  ): File {
    checkSourceId(sourceId)
    require(!Files.isSymbolicLink(shardRoot.toPath())) { "Mosaic shard root must not be a symbolic link" }
    val shard = File(shardRoot, "$sourceId$SUFFIX")
    require(shard.canonicalFile.toPath().startsWith(shardRoot.canonicalFile.toPath())) {
      "Mosaic shard path escapes output root: $shard"
    }
    return shard
  }

  fun sourceIdForShard(
    shardRoot: File,
    shard: File,
  ): String? {
    val root = shardRoot.absoluteFile.toPath().normalize()
    val path = shard.absoluteFile.toPath().normalize()
    require(path.startsWith(root)) { "Mosaic shard is outside output root: $path" }
    val relative = root.relativize(path).toString().replace(File.separatorChar, '/')
    if (!relative.endsWith(SUFFIX)) return null
    return relative.removeSuffix(SUFFIX).also(::checkSourceId)
  }

  fun sourceHash(source: File): String = ProtocolJson.hash(source.readBytes())

  internal fun checkSourceId(id: String) {
    require(id.isNotBlank() && !id.startsWith('/') && '\\' !in id && id.split('/').none { it == ".." || it == "." }) {
      "Invalid Mosaic shard source identity $id"
    }
  }
}

/** Internal compiler output. Shards are never packaged or accepted as dependency metadata. */
data class SourceShard(
  val sourceId: String,
  val module: ModuleContract,
  val limitations: List<String> = emptyList(),
  val binaryLocators: Map<String, String> = emptyMap(),
  val context: ExtractionEnvironment,
  val sourceHash: String,
)

@Serializable
private data class ShardEnvelope(
  val shardVersion: Int,
  val sourceId: String,
  val context: ExtractionEnvironment,
  val sourceHash: String,
  val payload: WirePayload,
  val integrityHash: String,
)

object SourceShardCodec {
  private const val SHARD_VERSION = 3
  private val json =
    Json {
      classDiscriminator = "kind"
      encodeDefaults = true
      explicitNulls = true
    }

  fun encode(shard: SourceShard): ByteArray {
    SourceShardPaths.checkSourceId(shard.sourceId)
    require(Regex("[0-9a-f]{64}").matches(shard.sourceHash)) { "Invalid Mosaic shard source hash" }
    val payload =
      WirePayload(
        shard.module.toWire(),
        shard.limitations.distinct().sorted(),
        shard.binaryLocators.toSortedMap().map { (id, locator) -> WireLocator(id, locator) },
      )
    val envelope = ShardEnvelope(SHARD_VERSION, shard.sourceId, shard.context, shard.sourceHash, payload, "")
    return (json.encodeToString(envelope.copy(integrityHash = integrityHash(envelope))) + "\n")
      .toByteArray(Charsets.UTF_8)
  }

  fun decode(bytes: ByteArray): SourceShard {
    val envelope = ProtocolJson.decode<ShardEnvelope>(ProtocolJson.parse(bytes, "shard"))
    require(envelope.shardVersion == SHARD_VERSION) { "Unsupported Mosaic shard version ${envelope.shardVersion}" }
    require(envelope.integrityHash == integrityHash(envelope)) { "Mosaic shard integrity hash mismatch" }
    SourceShardPaths.checkSourceId(envelope.sourceId)
    require(Regex("[0-9a-f]{64}").matches(envelope.sourceHash)) { "Invalid Mosaic shard source hash" }
    val locators = linkedMapOf<String, String>()
    envelope.payload.binaryLocators.forEach {
      require(locators.putIfAbsent(it.id, it.locator) == null) { "Duplicate Mosaic shard locator ${it.id}" }
    }
    return SourceShard(
      envelope.sourceId,
      envelope.payload.module.toModel(),
      envelope.payload.limitations,
      locators,
      envelope.context,
      envelope.sourceHash,
    )
  }

  private fun integrityHash(envelope: ShardEnvelope): String =
    ProtocolJson.hash(json.encodeToString(envelope.copy(integrityHash = "")).toByteArray(Charsets.UTF_8))

  fun assemble(
    moduleId: String,
    shards: Collection<SourceShard>,
    context: ExtractionEnvironment,
    retainedRequirements: List<RuntimeRequirement> = context.production.runtimes,
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
      require(
        shard.context == context,
      ) { "Stale Mosaic shard environment in ${shard.sourceId}; recompile current sources" }
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
      context =
        context.production.copy(
          runtimes = CompatibilityProtocol.merge(context.production.runtimes, retainedRequirements),
        ),
      limitations = limitations.distinct().sorted(),
      binaryLocators = locators,
    )
  }
}
