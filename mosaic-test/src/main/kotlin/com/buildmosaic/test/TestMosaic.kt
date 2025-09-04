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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.KClass

/**
 * Test wrapper around Mosaic that provides assertion methods for testing tiles.
 * Maintains context about mocked tiles for verification.
 */
class TestMosaic(
  private val mosaic: Mosaic,
  private val mockTiles: Map<KClass<*>, Tile>,
) {
  /**
   * Gets the underlying Mosaic instance.
   */
  val request: MosaicRequest get() = mosaic.request

  /**
   * Gets the mocked tiles for verification purposes.
   */
  fun getMockTiles(): Map<KClass<*>, Tile> = mockTiles

  // Delegate to the underlying mosaic
  fun <T : Tile> getTile(tileClass: KClass<T>): T = mosaic.getTile(tileClass)

  inline fun <reified T : Tile> getTile(): T = getTile(T::class)

  suspend fun <R> assertEquals(
    tileClass: KClass<out SingleTile<R>>,
    expected: R,
  ) {
    val tile = getTile(tileClass)
    val actual = tile.get()
    Assertions.assertEquals(expected, actual)
  }

  suspend inline fun <reified T : SingleTile<R>, R> assertEquals(expected: R) {
    assertEquals(T::class, expected)
  }

  suspend fun <R> assertEquals(
    tileClass: KClass<out SingleTile<R>>,
    expected: R,
    message: String,
  ) {
    val tile = getTile(tileClass)
    val actual = tile.get()
    Assertions.assertEquals(expected, actual, message)
  }

  suspend inline fun <reified T : SingleTile<R>, R> assertEquals(
    expected: R,
    message: String,
  ) {
    assertEquals(T::class, expected, message)
  }

  suspend fun <R> assertEquals(
    tileClass: KClass<out MultiTile<R, *>>,
    keys: List<String>,
    expected: Map<String, R>,
  ) {
    val tile = getTile(tileClass)
    val actual = tile.getByKeys(keys)
    Assertions.assertEquals(expected, actual)
  }

  suspend inline fun <reified T : MultiTile<R, *>, R> assertEquals(
    keys: List<String>,
    expected: Map<String, R>,
  ) {
    val tile = getTile(T::class)
    Assertions.assertEquals(expected, tile.getByKeys(keys))
  }

  suspend fun <R> assertEquals(
    tileClass: KClass<out MultiTile<R, *>>,
    keys: List<String>,
    expected: Map<String, R>,
    message: String = "MultiTile result does not match expected value",
  ) {
    val tile = getTile(tileClass)
    val actual = tile.getByKeys(keys)
    Assertions.assertEquals(expected, actual, message)
  }

  suspend inline fun <reified T : MultiTile<R, *>, R> assertEquals(
    keys: List<String>,
    expected: Map<String, R>,
    message: String,
  ) {
    val tile = getTile(T::class)
    Assertions.assertEquals(expected, tile.getByKeys(keys), message)
  }

  suspend fun <T : SingleTile<*>> assertThrows(
    tileClass: KClass<out T>,
    expectedException: Class<out Throwable>,
  ) {
    val tile = getTile(tileClass)
    Assertions.assertThrows(expectedException) {
      runBlocking {
        tile.get()
      }
    }
  }

  suspend inline fun <reified T : SingleTile<*>> assertThrows(expectedException: Class<out Throwable>) {
    assertThrows(T::class, expectedException)
  }

  suspend fun <T : SingleTile<*>> assertThrows(
    tileClass: KClass<out T>,
    expectedException: Class<out Throwable>,
    message: String,
  ) {
    val tile = getTile(tileClass)
    try {
      runBlocking { tile.get() }
      Assertions.fail<Nothing>(message)
    } catch (e: Throwable) {
      if (!expectedException.isInstance(e)) {
        Assertions.fail<Nothing>("Expected ${expectedException.simpleName} but got ${e::class.simpleName}: $message")
      }
    }
  }

  suspend inline fun <reified T : SingleTile<*>> assertThrows(
    expectedException: Class<out Throwable>,
    message: String,
  ) {
    assertThrows(T::class, expectedException, message)
  }

  suspend fun <T : MultiTile<R, *>, R> assertThrows(
    tileClass: KClass<out T>,
    keys: List<String>,
    expectedException: Class<out Throwable>,
  ) {
    val tile = getTile(tileClass)
    Assertions.assertThrows(expectedException) {
      runBlocking {
        tile.getByKeys(keys)
      }
    }
  }

  suspend inline fun <reified T : MultiTile<R, *>, R> assertThrows(
    keys: List<String>,
    expectedException: Class<out Throwable>,
  ) {
    assertThrows(T::class, keys, expectedException)
  }

  suspend fun <T : MultiTile<R, *>, R> assertThrows(
    tileClass: KClass<out T>,
    keys: List<String>,
    expectedException: Class<out Throwable>,
    message: String,
  ) {
    val tile = getTile(tileClass)
    try {
      runBlocking { tile.getByKeys(keys) }
      Assertions.fail<Nothing>(message)
    } catch (e: Throwable) {
      if (!expectedException.isInstance(e)) {
        Assertions.fail<Nothing>("Expected ${expectedException.simpleName} but got ${e::class.simpleName}: $message")
      }
    }
  }

  suspend inline fun <reified T : MultiTile<R, *>, R> assertThrows(
    keys: List<String>,
    expectedException: Class<out Throwable>,
    message: String,
  ) {
    assertThrows(T::class, keys, expectedException, message)
  }
}
