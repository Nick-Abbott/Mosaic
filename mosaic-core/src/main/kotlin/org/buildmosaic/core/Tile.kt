/*
 * Copyright 2025 Nicholas Abbott
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.buildmosaic.core

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Base sealed class representing a unit of work in the Mosaic framework.
 *
 * Tiles are the fundamental building blocks of Mosaic applications, representing
 * individual pieces of functionality or data retrieval. They can be composed together
 * to build complex operations while maintaining type safety and concurrency control.
 *
 * There are two main types of tiles:
 * 1. [SingleTile] - For operations that return a single result
 * 2. [MultiTile] - For batch operations that return multiple results
 *
 * @property mosaic The [Mosaic] instance that created this tile
 * @see SingleTile
 * @see MultiTile
 */
sealed class Tile(protected val mosaic: Mosaic)

/**
 * Abstract base class for tiles that retrieve a single value.
 *
 * [SingleTile] provides built-in caching and thread-safety for operations that
 * return a single result. The result is cached after the first retrieval and
 * subsequent calls will return the cached value.
 *
 * @param T The type of value this tile retrieves
 * @property mosaic The [Mosaic] instance that created this tile
 * @see Tile
 */
abstract class SingleTile<T>(mosaic: Mosaic) : Tile(mosaic) {
  private var retrieveDeferred: Deferred<T>? = null
  private val mutex = Mutex()

  /**
   * Retrieves the value, either from cache or by executing [retrieve] if not yet cached.
   *
   * This method is thread-safe and will only execute [retrieve] once, even when called
   * concurrently from multiple coroutines.
   *
   * @return The retrieved value of type [T]
   * @see retrieve
   */
  suspend fun get(): T {
    // Fast path - check without lock
    retrieveDeferred?.let { return it.await() }

    // Slow path - acquire lock and double-check
    mutex.withLock {
      retrieveDeferred?.let { return it.await() }

      // Create and store deferred
      retrieveDeferred = coroutineScope { async { retrieve() } }
      return retrieveDeferred!!.await()
    }
  }

  /**
   * Abstract method that performs the actual value retrieval.
   *
   * Implement this method to define how the value should be retrieved.
   * This method will only be called once per [Mosaic] instance, as the result is cached.
   *
   * @return The retrieved value of type [T]
   */
  protected abstract suspend fun retrieve(): T
}

/**
 * Abstract base class for tiles that retrieve multiple values in a batch.
 *
 * [MultiTile] is optimized for batch operations where multiple related values can be
 * retrieved more efficiently together than individually. It provides built-in caching
 * and deduplication of keys.
 *
 * @param SingleType The type of individual values
 * @param MultiType The type of the batch responses
 * @property mosaic The [Mosaic] instance that created this tile
 */
abstract class MultiTile<SingleType, MultiType>(mosaic: Mosaic) : Tile(mosaic) {
  private val cache = ConcurrentHashMap<String, Deferred<SingleType>>()
  private val mutex = Mutex()

  /**
   * Retrieves multiple values by their keys, using batching for efficiency.
   *
   * This method will:
   * 1. Return cached values immediately when available
   * 2. Batch remaining keys and fetch them in a single operation
   * 3. Cache all results for future use
   *
   * @param keys The list of keys to retrieve
   * @return A map of keys to their corresponding values
   * @see retrieveForKeys
   * @see normalize
   */
  suspend fun getByKeys(keys: List<String>): Map<String, SingleType> {
    // Fast path - all keys are in cache
    if (keys.all(cache::containsKey)) {
      return keys.associate { it to cache[it]!!.await() }
    }

    // Slow path - acquire lock and double-check
    return mutex.withLock {
      val missingKeys = keys.filterNot { cache.containsKey(it) }

      // Double check
      if (missingKeys.isEmpty()) {
        return@withLock keys.associate { it to cache[it]!!.await() }
      }

      // Start retrieval for all missing keys
      val responseDeferred = coroutineScope { async { retrieveForKeys(missingKeys) } }

      // Create deferreds for all missing keys immediately
      missingKeys.forEach { key ->
        cache[key] =
          coroutineScope {
            async {
              val response = responseDeferred.await()
              normalize(key, response)
            }
          }
      }

      // Wait for all results and return
      keys.associate { it to cache[it]!!.await() }
    }
  }

  /**
   * Convenience method for retrieving values by a variable number of keys.
   *
   * @param keys The keys to retrieve
   * @return A map of keys to their corresponding values
   * @see retrieveForKeys
   */
  suspend fun getByKeys(vararg keys: String): Map<String, SingleType> {
    return getByKeys(keys.toList())
  }

  /**
   * Performs the batch retrieval of values for the given keys.
   *
   * @param keys The list of keys to retrieve in a single batch
   * @return A batch response containing all requested values
   */
  abstract suspend fun retrieveForKeys(keys: List<String>): MultiType

  /**
   * Extracts a single value from the batch response.
   *
   * @param key The key to extract from the batch response
   * @param response The batch response
   * @return The extracted value
   */
  abstract fun normalize(
    key: String,
    response: MultiType,
  ): SingleType
}
