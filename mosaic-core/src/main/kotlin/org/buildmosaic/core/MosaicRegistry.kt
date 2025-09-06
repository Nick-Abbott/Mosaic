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

import kotlin.reflect.KClass

/**
 * A registry for managing the construction of [Tile] instances in the Mosaic framework.
 * This should be used as a Singleton shared between all Mosaic instances.
 *
 * [MosaicRegistry] is responsible for:
 * - Registering tile constructors
 * - Instantiating tiles on demand
 *
 * @constructor Creates a new MosaicRegistry instance
 */
class MosaicRegistry {
  private val constructors = mutableMapOf<KClass<out Tile>, (Mosaic) -> Tile>()

  /**
   * Registers a constructor function for a specific [Tile] type.
   *
   * @param T The type of tile to register
   * @param tileClass The [KClass] of the tile to register
   * @param constructor A function that creates a new instance of the tile
   *
   * ```kotlin
   * val registry = MosaicRegistry()
   * registry.register(MyTile::class) { mosaic -> MyTile(mosaic) }
   * ```
   */
  fun <T : Tile> register(
    tileClass: KClass<T>,
    constructor: (Mosaic) -> T,
  ) {
    constructors[tileClass] = constructor
  }

  /**
   * Retrieves a new instance of the specified [Tile] class.
   *
   * @param T The type of tile to retrieve
   * @param tileClass The [KClass] of the tile to instantiate
   * @param mosaic The [Mosaic] instance to pass to the tile's constructor
   * @return A new instance of the requested tile
   * @throws IllegalArgumentException if no constructor is registered for the specified tile class
   */
  fun <T : Tile> getInstance(
    tileClass: KClass<T>,
    mosaic: Mosaic,
  ): T {
    val constructor =
      constructors[tileClass]
        ?: throw IllegalArgumentException(
          "No constructor registered for ${tileClass.simpleName}. " +
            "Did you forget to register your tiles with mosaic-build-plugin?",
        )

    @Suppress("UNCHECKED_CAST")
    return constructor(mosaic) as T
  }
}
