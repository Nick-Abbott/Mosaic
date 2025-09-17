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

package org.buildmosaic.test

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.test.TestDispatcher
import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.MosaicImpl
import org.buildmosaic.core.MultiTile
import org.buildmosaic.core.Tile
import org.buildmosaic.core.injection.Canvas
import kotlin.reflect.KClass
import kotlin.test.assertEquals as testAssertEquals
import kotlin.test.assertFailsWith as testAssertFailsWith

/**
 * A test wrapper around [Mosaic] that provides assertion methods for testing [Tile] implementations.
 *
 * This class is the main entry point for writing tests with Mosaic. It extends the standard [Mosaic]
 * functionality with testing-specific methods and assertions.
 *
 * This class should be built using [TestMosaicBuilder] for advanced usage.
 *
 * ```kotlin
 * // In a test class
 * private val testMosaic = TestMosaicBuilder()
 *   .withMockTile<MyTile>("test data")
 *   .build()
 *
 * @Test
 * fun `test tile behavior`() = runBlocking {
 *   // When
 *   val result = testMosaic.getTile<MyTile>().get()
 *
 *   // Then
 *   testMosaic.assertEquals(MyTile::class, "test data")
 * }
 * ```
 *
 */
class TestMosaic(
  canvas: Canvas,
  private val mockTileCache: Map<Tile<*>, Tile<*>>,
  private val mockMultiTileCache: Map<MultiTile<*, *>, MultiTile<*, *>>,
  dispatcher: TestDispatcher,
) : MosaicImpl(canvas, dispatcher) {
  /**
   * Asserts that a [Tile] returns the expected value.
   *
   * @param V The type of value the tile returns
   * @param tile The tile to test
   * @param expected The expected value
   * @throws AssertionError if the actual value doesn't match the expected value
   *
   * ```kotlin
   * testMosaic.assertEquals(MyTile, "expected value")
   * ```
   */
  suspend fun <V> assertEquals(
    tile: Tile<V>,
    expected: V,
  ) = testAssertEquals(expected, compose(tile))

  /**
   * Asserts that a [Tile] returns the expected value with a custom failure message.
   *
   * @param V The type of value the tile returns
   * @param tile The tile to test
   * @param expected The expected value
   * @param message The message to include in case of assertion failure
   * @throws AssertionError if the actual value doesn't match the expected value
   *
   * ```kotlin
   * testMosaic.assertEquals(
   *   tile = MyTile,
   *   expected = "expected value",
   *   message = "The tile did not return the expected value"
   * )
   * ```
   */
  suspend fun <V> assertEquals(
    tile: Tile<V>,
    expected: V,
    message: String,
  ) = testAssertEquals(expected, compose(tile), message)

  /**
   * Asserts that a [MultiTile] returns the expected values for the given keys.
   *
   * @param K The type of keys in the response map
   * @param V The type of values in the response map
   * @param tile The tile to test
   * @param keys The list of keys to request from the tile
   * @param expected The expected map of keys to values
   * @throws AssertionError if the actual values don't match the expected values
   *
   * ```kotlin
   * testMosaic.assertEquals(
   *   tile = UserTile,
   *   keys = listOf("user1", "user2"),
   *   expected = mapOf("user1" to User("user1"), "user2" to User("user2"))
   * )
   * ```
   */
  suspend fun <K : Any, V> assertEquals(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
    expected: Map<K, V>,
  ) = testAssertEquals(expected, compose(tile, keys))

  /**
   * Asserts that a [MultiTile] returns the expected values for the given keys with a custom failure message.
   *
   * @param K The type of keys in the response map
   * @param V The type of values in the response map
   * @param tile The tile to test
   * @param keys The list of keys to request from the tile
   * @param expected The expected map of keys to values
   * @param message The message to include in case of assertion failure
   * @throws AssertionError if the actual values don't match the expected values
   *
   * ```kotlin
   * testMosaic.assertEquals(
   *   tile = UserTile,
   *   keys = listOf("user1", "user2"),
   *   expected = mapOf("user1" to User("user1"), "user2" to User("user2")),
   *   message = "User data does not match expected values"
   * )
   * ```
   */
  suspend fun <K : Any, V> assertEquals(
    tile: MultiTile<K, V>,
    keys: List<K>,
    expected: Map<K, V>,
    message: String,
  ) = testAssertEquals(expected, compose(tile, keys), message)

  /**
   * Asserts that a [Tile] throws the expected exception when retrieved.
   *
   * @param tile The tile to test
   * @param expectedException The exception class that is expected to be thrown
   * @throws AssertionError if the tile does not throw the expected exception
   *
   * ```kotlin
   * testMosaic.assertThrows(
   *   tile = FailingTile,
   *   expectedException = IllegalStateException::class
   * )
   * ```
   */
  suspend fun assertThrows(
    tile: Tile<*>,
    expectedException: KClass<out Throwable>,
  ) = testAssertFailsWith(expectedException) { compose(tile) }

  /**
   * Asserts that a [Tile] throws the expected exception with a custom failure message.
   *
   * @param tile The tile to test
   * @param expectedException The exception class that is expected to be thrown
   * @param message The message to include in case of assertion failure
   * @throws AssertionError if the tile does not throw the expected exception
   *
   * ```kotlin
   * testMosaic.assertThrows(
   *   tile = FailingTile,
   *   expectedException = IllegalStateException::class,
   *   message = "Expected IllegalStateException but got a different exception"
   * )
   * ```
   */
  suspend fun assertThrows(
    tile: Tile<*>,
    expectedException: KClass<out Throwable>,
    message: String,
  ) = testAssertFailsWith(expectedException, message) { compose(tile) }

  /**
   * Asserts that a [MultiTile] throws the expected exception when retrieved with the given keys.
   *
   * @param K The type of keys in the response map
   * @param tile The tile to test
   * @param keys The list of keys to request from the tile
   * @param expectedException The exception class that is expected to be thrown
   * @throws AssertionError if the tile does not throw the expected exception
   *
   * ```kotlin
   * testMosaic.assertThrows(
   *   tile = FailingUserTile,
   *   keys = listOf("user1", "user2"),
   *   expectedException = IllegalStateException::class
   * )
   * ```
   */
  suspend fun <K : Any> assertThrows(
    tile: MultiTile<K, *>,
    keys: List<K>,
    expectedException: KClass<out Throwable>,
  ) = testAssertFailsWith(expectedException) { compose(tile, keys) }

  // Mosaic interface passthroughs
  @Suppress("UNCHECKED_CAST")
  override suspend fun <V> compose(tile: Tile<V>): V {
    return super.compose(cacheOrTile(tile))
  }

  override fun <V> composeAsync(tile: Tile<V>): Deferred<V> = super.composeAsync(cacheOrTile(tile))

  override fun <K : Any, V> composeAsync(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, Deferred<V>> = super.composeAsync(cacheOrTile(tile), keys)

  @Suppress("UNCHECKED_CAST")
  private fun <V> cacheOrTile(tile: Tile<V>): Tile<V> = mockTileCache[tile] as Tile<V>? ?: tile

  @Suppress("UNCHECKED_CAST")
  private fun <K : Any, V> cacheOrTile(tile: MultiTile<K, V>) = mockMultiTileCache[tile] as MultiTile<K, V>? ?: tile
}
