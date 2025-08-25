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

import kotlin.reflect.KClass

class Mosaic(
  private val registry: MosaicRegistry,
  val request: MosaicRequest,
) {
  private val tileCache = mutableMapOf<KClass<*>, Tile>()

  fun <T : Tile> getTile(tileClass: KClass<T>): T {
    return tileCache[tileClass]?.let { cachedTile ->
      @Suppress("UNCHECKED_CAST")
      cachedTile as T
    } ?: run {
      val newTile = registry.getInstance(tileClass, this)
      tileCache[tileClass] = newTile
      newTile
    }
  }

  inline fun <reified T : Tile> getTile(): T {
    return getTile(T::class)
  }
}
