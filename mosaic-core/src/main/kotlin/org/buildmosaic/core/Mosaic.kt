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

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * The main entry point for the Mosaic framework.
 * Responsible for managing the lifecycle and caching of [Tile] instances.
 *
 * Mosaic provides a request-scoped context for tile execution, handling concurrency and caching automatically.
 * Each request should create a new [Mosaic] instance, which will be used to resolve and execute tiles.
 *
 * @property request The [MosaicRequest] containing context and data for the current request.
 * @property registry The [MosaicRegistry] used to resolve tile instances.
 * @constructor Creates a new Mosaic instance for the given request and registry.
 * @throws IllegalArgumentException if registry or request is null
 */
class Mosaic(
  private val registry: MosaicRegistry,
  val request: MosaicRequest,
) {
  private val tileCache = ConcurrentHashMap<KClass<*>, Tile>()
  private val lock = Any()

  /**
   * Retrieves an instance of the specified [Tile] class, creating it if necessary.
   * This method is thread-safe and will return the same instance for subsequent calls with the same tile class.
   *
   * @param T The type of tile to retrieve
   * @param tileClass The [KClass] of the tile to retrieve
   * @return An instance of the requested tile class
   * @throws IllegalArgumentException if the tile is unregistered
   * For a version that infers the type parameter, see [getTile] with reified type parameter
   */
  fun <T : Tile> getTile(tileClass: KClass<T>): T {
    @Suppress("UNCHECKED_CAST")
    tileCache[tileClass]?.let { return it as T }

    synchronized(lock) {
      @Suppress("UNCHECKED_CAST")
      tileCache[tileClass]?.let { return it as T }

      val newTile = registry.getInstance(tileClass, this)
      tileCache[tileClass] = newTile
      return newTile
    }
  }

  /**
   * Retrieves an instance of the specified [Tile] class using reified type parameters.
   * This is a convenience method that infers the tile class from the type parameter.
   *
   * ```kotlin
   * val userTile = mosaic.getTile<UserTile>()
   * ```
   *
   * @param T The type of tile to retrieve (inferred from the return type)
   * @return An instance of the requested tile class
   * @throws IllegalArgumentException if the tile is unregistered
   */
  inline fun <reified T : Tile> getTile(): T {
    return getTile(T::class)
  }
}
