package org.buildmosaic.core.internal

import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.MultiTile
import org.buildmosaic.core.Tile
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.withMosaicExecution

/** Unsupported bridge for Mosaic's own modules; no source or binary compatibility guarantees. */
@RequiresOptIn(
  message = "Internal Mosaic test bridge. Only for mosaic-test; not a consumer extension API.",
  level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class InternalMosaicTestApi

/**
 * Runs mosaic-test's frozen substitutions through the ordinary scoped runtime.
 * Unsupported: no source or binary compatibility guarantees.
 *
 * Kotlin compilation associations only support a single target. Cross-project task-level friend
 * paths compile, but [IDE support](https://youtrack.jetbrains.com/issue/KTIJ-31881) is unresolved.
 * Keep this narrow, guarded bridge until friendship supports both the build and IDE import.
 */
@InternalMosaicTestApi
@JvmSynthetic
suspend fun <R> Canvas.withTestMosaicExecution(
  tiles: Map<Tile<*>, Tile<*>>,
  multiTiles: Map<MultiTile<*, *>, MultiTile<*, *>>,
  block: suspend Mosaic.() -> R,
): R = withMosaicExecution(this, tiles, multiTiles, block)
