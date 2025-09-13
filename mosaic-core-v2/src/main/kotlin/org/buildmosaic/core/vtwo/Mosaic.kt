package org.buildmosaic.core.vtwo

import kotlinx.coroutines.Deferred
import org.buildmosaic.core.vtwo.injection.Canvas
import org.buildmosaic.core.vtwo.injection.Scene

/**
 * Per-request context used to execute tiles and access dependencies.
 */
interface Mosaic {
  val canvas: Canvas
  val scene: Scene

  suspend fun <V> composeAsync(tile: Tile<V>): Deferred<V>

  suspend fun <V> compose(tile: Tile<V>): V

  suspend fun <K : Any, V> composeAsync(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, Deferred<V>>

  suspend fun <K : Any, V> compose(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, V>

  suspend fun <K : Any, V> compose(
    tile: MultiTile<K, V>,
    key: K,
  ): V
}

inline fun <reified T : Any> Mosaic.source(): T = canvas.source(T::class)
