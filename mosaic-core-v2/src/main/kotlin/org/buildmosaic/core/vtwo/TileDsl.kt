package org.buildmosaic.core.vtwo

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Representation of a single-value tile. */
class Tile<T>(internal val block: suspend Mosaic.() -> T)

fun <T> singleTile(block: suspend Mosaic.() -> T): Tile<T> = Tile(block)

/** Representation of a multi-value tile. */
class MultiTile<K, V>(internal val block: suspend Mosaic.(Set<K>) -> Map<K, V>)

fun <K, V> multiTile(block: suspend Mosaic.(Set<K>) -> Map<K, V>): MultiTile<K, V> = MultiTile(block)

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
fun <K, V> perKeyTile(fetch: suspend Mosaic.(K) -> V): MultiTile<K, V> =
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
fun <K, V> chunkedMultiTile(
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

/** Simple request context that can carry arbitrary attributes. */
data class MosaicRequest(val attributes: Map<String, Any?> = emptyMap())
