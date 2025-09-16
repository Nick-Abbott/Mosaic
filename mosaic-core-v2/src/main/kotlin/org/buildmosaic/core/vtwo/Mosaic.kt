package org.buildmosaic.core.vtwo

import kotlinx.coroutines.Deferred
import org.buildmosaic.core.vtwo.injection.Canvas
import org.buildmosaic.core.vtwo.injection.CanvasKey

/**
 * Per-request context used to execute tiles and access dependencies.
 */
interface Mosaic {
  val canvas: Canvas

  /**
   * Retrieve the value of a [Tile] wrapped in a deferred for awaiting later
   *
   * @param V the type of the [Tile] return value
   * @param tile the [Tile] to retrieve
   */
  suspend fun <V> composeAsync(tile: Tile<V>): Deferred<V>

  /**
   * Await the value of a [Tile]
   *
   * @param V the type of the [Tile] return value
   * @param tile the [Tile] to retrieve
   */
  suspend fun <V> compose(tile: Tile<V>): V

  /**
   * Retrieve the value of a [MultiTile] wrapped in a deferred for awaiting later
   *
   * @param K the type of the [MultiTile] keys
   * @param V the type of the [MultiTile] return value
   * @param tile the [MultiTile] to retrieve
   * @param keys the keys to be retrieved
   */
  suspend fun <K : Any, V> composeAsync(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, Deferred<V>>

  /**
   * Await the value of a [MultiTile]
   *
   * @param K the type of the [MultiTile] keys
   * @param V the type of the [MultiTile] return value
   * @param tile the [MultiTile] to retrieve
   * @param keys the keys to be retrieved
   */
  suspend fun <K : Any, V> compose(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, V>

  /**
   * Retrieve a single value from a [MultiTile] wrapped in a deferred for awaiting later
   *
   * @param K the type of the [MultiTile] keys
   * @param V the type of the [MultiTile] return value
   * @param tile the [MultiTile] to retrieve
   * @param key the single key to be retrieved
   */
  suspend fun <K : Any, V> composeAsync(
    tile: MultiTile<K, V>,
    key: K,
  ): Deferred<V>

  /**
   * Await a single value from a [MultiTile]
   *
   * @param K the type of the [MultiTile] keys
   * @param V the type of the [MultiTile] return value
   * @param tile the [MultiTile] to retrieve
   * @param key the single key to be retrieved
   */
  suspend fun <K : Any, V> compose(
    tile: MultiTile<K, V>,
    key: K,
  ): V
}

/**
 * Retrieves a dependency from the canvas using reified type parameters.
 *
 * @param T The type of the dependency to retrieve
 * @param qualifier Optional qualifier to distinguish between multiple instances of the same type
 * @return The dependency instance
 * @throws MosaicMissingKeyException if the dependency is not found
 */
inline fun <reified T : Any> Mosaic.source(qualifier: String? = null) = canvas.source(T::class, qualifier)

/**
 * Retrieves a dependency from the canvas using a [CanvasKey].
 *
 * @param T The type of the dependency to retrieve
 * @param key The canvas key identifying the dependency
 * @return The dependency instance
 * @throws MosaicMissingKeyException if the dependency is not found
 */
fun <T : Any> Mosaic.source(key: CanvasKey<T>) = canvas.source(key)

/**
 * Retrieves a dependency from the canvas using reified type parameters, returning null if not found.
 *
 * @param T The type of the dependency to retrieve
 * @param qualifier Optional qualifier to distinguish between multiple instances of the same type
 * @return The dependency instance or null if not found
 */
inline fun <reified T : Any> Mosaic.sourceOr(qualifier: String? = null) = canvas.sourceOr(T::class, qualifier)

/**
 * Retrieves a dependency from the canvas using a [CanvasKey], returning null if not found.
 *
 * @param T The type of the dependency to retrieve
 * @param key The canvas key identifying the dependency
 * @return The dependency instance or null if not found
 */
fun <T : Any> Mosaic.sourceOr(key: CanvasKey<T>) = canvas.sourceOr(key)
