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

package com.abbott.mosaic

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

sealed class Tile(protected val mosaic: Mosaic)

abstract class SingleTile<T>(mosaic: Mosaic) : Tile(mosaic) {
  private var retrieveDeferred: Deferred<T>? = null

  suspend fun get(): T {
    val deferred = retrieveDeferred
    if (deferred != null) {
      return deferred.await()
    }

    return kotlinx.coroutines.coroutineScope {
      val newDeferred =
        async {
          retrieve()
        }
      retrieveDeferred = newDeferred
      newDeferred.await()
    }
  }

  protected abstract suspend fun retrieve(): T
}

abstract class MultiTile<SingleType, MultiType>(mosaic: Mosaic) : Tile(mosaic) {
  private val cache = mutableMapOf<String, Deferred<SingleType>>()

  suspend fun getByKeys(keys: List<String>): Map<String, SingleType> {
    return kotlinx.coroutines.coroutineScope {
      val missingKeys = mutableListOf<String>()
      val existingDeferreds = mutableMapOf<String, Deferred<SingleType>>()

      // Check cache for existing deferreds
      for (key in keys) {
        val deferred = cache[key]
        if (deferred != null) {
          existingDeferreds[key] = deferred
        } else {
          missingKeys.add(key)
        }
      }

      // Create deferreds for missing keys and start retrieval immediately
      val missingDeferreds = mutableMapOf<String, Deferred<SingleType>>()
      if (missingKeys.isNotEmpty()) {
        val batchResponse = retrieveForKeys(missingKeys)

        for (key in missingKeys) {
          val deferred =
            async {
              normalize(key, batchResponse)
            }
          cache[key] = deferred
          missingDeferreds[key] = deferred
        }
      }

      // Await all results in parallel
      val result = mutableMapOf<String, SingleType>()

      // Await existing deferreds
      existingDeferreds.forEach { (key, deferred) ->
        result[key] = deferred.await()
      }

      // Await missing deferreds
      missingDeferreds.forEach { (key, deferred) ->
        result[key] = deferred.await()
      }

      result
    }
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
