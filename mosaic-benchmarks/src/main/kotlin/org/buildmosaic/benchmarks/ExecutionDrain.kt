package org.buildmosaic.benchmarks

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.Tile

/** Complete the request after composition, then wait for every owned execution and attached child. */
internal suspend fun composeAndDrain(mosaic: Mosaic, tile: Tile<Int>): Int {
  val value = mosaic.compose(tile)
  val job = checkNotNull((mosaic as CoroutineScope).coroutineContext[Job]) as CompletableJob
  job.complete()
  job.join()
  return value
}
