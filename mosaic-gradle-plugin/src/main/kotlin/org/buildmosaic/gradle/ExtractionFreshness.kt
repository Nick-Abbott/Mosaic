package org.buildmosaic.gradle

import org.buildmosaic.analysis.ExtractionEnvironmentCodec
import org.buildmosaic.analysis.SourceShardCodec
import org.buildmosaic.analysis.SourceShardPaths
import java.io.File
import java.io.IOException

/** Kotlin's ABI cache does not track Runtime resources or Mosaic's disposable shard environment. */
internal fun requiresFreshMosaicExtraction(
  contextFile: File,
  shards: File,
  sourceRoot: File,
  sources: Set<File>,
): Boolean {
  val environment = ExtractionEnvironmentCodec.decode(contextFile.readBytes())
  return sources.filter { it.extension == "kt" }.any { source ->
    try {
      val id = SourceShardPaths.sourceId(sourceRoot, source)
      val shard = SourceShardPaths.shardFile(shards, id)
      !shard.isFile || SourceShardCodec.decode(shard.readBytes()).context != environment
    } catch (_: IllegalArgumentException) {
      true
    } catch (_: IOException) {
      true
    }
  }
}
