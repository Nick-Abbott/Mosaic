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

import kotlin.reflect.KClass

class MosaicRegistry {
  private val constructors = mutableMapOf<KClass<out Tile>, (Mosaic) -> Tile>()

  fun <T : Tile> register(
    tileClass: KClass<T>,
    constructor: (Mosaic) -> T,
  ) {
    constructors[tileClass] = constructor
  }

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
