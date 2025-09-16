package org.buildmosaic.core

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Representation of a single-value tile in Mosaic.
 *
 * A tile encapsulates a suspending function that produces a single value of type [T].
 * Tiles are the fundamental building blocks of the Mosaic DSL and support automatic
 * caching, concurrency, and dependency injection.
 */
class Tile<T>(internal val block: suspend Mosaic.() -> T)

/**
 * Creates a single-value tile using the DSL.
 *
 * @param T The type of value this tile produces
 * @param block A suspending function that produces the tile's value
 * @return A new [Tile] instance
 *
 * ```kotlin
 * val userTile = singleTile {
 *   userService.getCurrentUser()
 * }
 * ```
 */
fun <T> singleTile(block: suspend Mosaic.() -> T): Tile<T> = Tile(block)

/**
 * Representation of a multi-value tile in Mosaic.
 *
 * A multi-tile encapsulates a suspending function that produces multiple values
 * based on a set of keys. Multi-tiles support automatic batching, caching,
 * and parallel execution.
 */
class MultiTile<K : Any, V>(internal val block: suspend Mosaic.(Set<K>) -> Map<K, V>)

/**
 * Creates a multi-value tile using the DSL.
 *
 * @param K The type of keys used to request values
 * @param V The type of values this tile produces
 * @param block A suspending function that produces a map of values for the given keys
 * @return A new [MultiTile] instance
 *
 * ```kotlin
 * val usersTile = multiTile<String, User> { userIds ->
 *   userService.getUsers(userIds)
 * }
 * ```
 */
fun <K : Any, V> multiTile(block: suspend Mosaic.(Set<K>) -> Map<K, V>): MultiTile<K, V> = MultiTile(block)

/**
 * Creates a [MultiTile] from a per-key fetch function.
 *
 * Each key is fetched independently in parallel and the results are aggregated
 * into a map. Example:
 *
 * ```kotlin
 * val userTile = perKeyTile<String, User> { id ->
 *   service.fetchUser(id)
 * }
 * ```
 */
fun <K : Any, V> perKeyTile(fetch: suspend Mosaic.(K) -> V): MultiTile<K, V> =
  multiTile { keys ->
    coroutineScope {
      keys
        .associateWith { key -> async { fetch(key) } }
        .mapValues { (_, deferred) -> deferred.await() }
    }
  }

/**
 * Creates a [MultiTile] that splits incoming keys into batches of [batchSize]
 * and merges the results from [fetch].
 *
 * ```kotlin
 * val productTile = chunkedMultiTile<String, Product>(50) { ids ->
 *   service.fetchProducts(ids)
 * }
 * ```
 */
fun <K : Any, V> chunkedMultiTile(
  batchSize: Int,
  fetch: suspend Mosaic.(List<K>) -> Map<K, V>,
): MultiTile<K, V> =
  multiTile { keys ->
    coroutineScope {
      val result = mutableMapOf<K, V>()
      keys
        .chunked(batchSize)
        .map { chunk -> async { fetch(chunk) } }
        .awaitAll()
        .forEach { map -> result += map }
      result
    }
  }
