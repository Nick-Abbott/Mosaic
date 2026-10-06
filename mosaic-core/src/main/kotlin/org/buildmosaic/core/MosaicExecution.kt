package org.buildmosaic.core

import kotlinx.coroutines.currentCoroutineContext
import org.buildmosaic.core.injection.Canvas

/** The single owner of runtime creation and teardown, including test executions. */
internal suspend fun <R> withMosaicExecution(
  canvas: Canvas,
  tiles: Map<Tile<*>, Tile<*>> = emptyMap(),
  multiTiles: Map<MultiTile<*, *>, MultiTile<*, *>> = emptyMap(),
  block: suspend Mosaic.() -> R,
): R {
  val mosaic = MosaicImpl(canvas, currentCoroutineContext(), tiles, multiTiles)
  try {
    return block(mosaic)
  } finally {
    mosaic.shutdown()
  }
}
