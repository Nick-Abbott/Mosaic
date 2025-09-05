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

package com.buildmosaic.test

import com.buildmosaic.core.Mosaic
import com.buildmosaic.core.MosaicRequest
import com.buildmosaic.core.MultiTile
import com.buildmosaic.core.SingleTile
import com.buildmosaic.core.Tile
import kotlin.reflect.KClass
import kotlin.test.assertEquals as testAssertEquals
import kotlin.test.assertFailsWith as testAssertFailsWith

/**
 * Test wrapper around Mosaic that provides assertion methods for testing tiles.
 * Maintains context about mocked tiles for verification.
 */
class TestMosaic(private val mosaic: Mosaic) {
  /**
   * Gets the underlying Mosaic instance.
   */
  val request: MosaicRequest get() = mosaic.request

  // Delegate to the underlying mosaic
  fun <T : Tile> getTile(tileClass: KClass<T>): T = mosaic.getTile(tileClass)

  inline fun <reified T : Tile> getTile(): T = getTile(T::class)

  suspend fun <R> assertEquals(
    tileClass: KClass<out SingleTile<R>>,
    expected: R,
  ) {
    val tile = getTile(tileClass)
    val actual = tile.get()
    testAssertEquals(expected, actual)
  }

  suspend fun <R> assertEquals(
    tileClass: KClass<out SingleTile<R>>,
    expected: R,
    message: String,
  ) {
    val tile = getTile(tileClass)
    val actual = tile.get()
    testAssertEquals(expected, actual, message)
  }

  suspend fun <R> assertEquals(
    tileClass: KClass<out MultiTile<R, *>>,
    keys: List<String>,
    expected: Map<String, R>,
  ) {
    val tile = getTile(tileClass)
    val actual = tile.getByKeys(keys)
    testAssertEquals(expected, actual)
  }

  suspend fun <R> assertEquals(
    tileClass: KClass<out MultiTile<R, *>>,
    keys: List<String>,
    expected: Map<String, R>,
    message: String,
  ) {
    val tile = getTile(tileClass)
    val actual = tile.getByKeys(keys)
    testAssertEquals(expected, actual, message)
  }

  suspend fun <T : SingleTile<*>> assertThrows(
    tileClass: KClass<out T>,
    expectedException: KClass<out Throwable>,
  ) {
    val tile = getTile(tileClass)
    testAssertFailsWith(expectedException) { tile.get() }
  }

  suspend fun <T : SingleTile<*>> assertThrows(
    tileClass: KClass<out T>,
    expectedException: KClass<out Throwable>,
    message: String,
  ) {
    val tile = getTile(tileClass)
    testAssertFailsWith(expectedException, message) { tile.get() }
  }

  suspend fun <T : MultiTile<R, *>, R> assertThrows(
    tileClass: KClass<out T>,
    keys: List<String>,
    expectedException: KClass<out Throwable>,
  ) {
    val tile = getTile(tileClass)
    testAssertFailsWith(expectedException) { tile.getByKeys(keys) }
  }

  suspend fun <T : MultiTile<R, *>, R> assertThrows(
    tileClass: KClass<out T>,
    keys: List<String>,
    expectedException: KClass<out Throwable>,
    message: String,
  ) {
    val tile = getTile(tileClass)
    testAssertFailsWith(expectedException, message) { tile.getByKeys(keys) }
  }
}
