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

class Mosaic(
  private val registry: MosaicRegistry,
  val request: MosaicRequest,
) {
  private val tileCache = ConcurrentHashMap<KClass<*>, Tile>()
  private val lock = Any()

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

  inline fun <reified T : Tile> getTile(): T {
    return getTile(T::class)
  }
}
