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

package com.buildmosaic.core

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

sealed class Tile(protected val mosaic: Mosaic)

abstract class SingleTile<T>(mosaic: Mosaic) : Tile(mosaic) {
  private var retrieveDeferred: Deferred<T>? = null
  private val mutex = Mutex()

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

  protected abstract suspend fun retrieve(): T
}

abstract class MultiTile<SingleType, MultiType>(mosaic: Mosaic) : Tile(mosaic) {
  private val cache = ConcurrentHashMap<String, Deferred<SingleType>>()
  private val mutex = Mutex()

  suspend fun getByKeys(keys: List<String>): Map<String, SingleType> {
    // Fast path - all keys are in cache
    if (keys.all(cache::containsKey)) {
      return keys.associateWith { cache[it]!!.await() }
    }

    // Slow path - acquire lock and double-check
    mutex.withLock {
      val missingKeys = keys.filterNot { cache.containsKey(it) }

      // Double check
      if (missingKeys.isEmpty()) return@withLock

      coroutineScope {
        // Create deferreds for missing keys and start retrieval immediately
        val newKeysResponseDeferred = async { retrieveForKeys(missingKeys) }
        missingKeys.forEach {
          cache[it] = async { normalize(it, newKeysResponseDeferred.await()) }
        }
      }
    }

    return keys.associateWith { cache[it]!!.await() }
  }

  suspend fun getByKeys(vararg keys: String): Map<String, SingleType> {
    return getByKeys(keys.toList())
  }

  abstract suspend fun retrieveForKeys(keys: List<String>): MultiType

  abstract fun normalize(
    key: String,
    response: MultiType,
  ): SingleType
}
