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

package org.buildmosaic.test.vtwo

import org.buildmosaic.core.vtwo.Mosaic
import org.buildmosaic.core.vtwo.MosaicRequest
import org.buildmosaic.core.vtwo.MultiTile
import org.buildmosaic.core.vtwo.Tile
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
class TestMosaic(private val mosaic: Mosaic) {
  /**
   * The [MosaicRequest] associated with this test instance.
   *
   * This provides access to the request context being used in the test.
   */
  val request: MosaicRequest get() = mosaic.request

  /**
   * Retrieves a value from a [Tile].
   *
   * @param V The type of value the tile returns
   * @param tile The tile to retrieve a value from
   * @return The value retrieved from the tile
   */
  suspend fun <V> get(tile: Tile<V>): V = mosaic.get<V>(tile)

  /**
   * Retrieves a map of values from a [MultiTile].
   *
   * @param K The type of keys in the multi-tile
   * @param V The type of values in the multi-tile
   * @param tile The multi-tile to retrieve values from
   * @param keys The keys to retrieve values for
   * @return A map of keys to their corresponding values
   */
  suspend fun <K, V> get(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, V> = mosaic.get<K, V>(tile, keys)

  /**
   * Retrieves a map of values from a [MultiTile].
   *
   * @param K The type of keys in the multi-tile
   * @param V The type of values in the multi-tile
   * @param tile The multi-tile to retrieve values from
   * @param keys The keys to retrieve values for
   * @return A map of keys to their corresponding values
   */
  suspend fun <K, V> get(
    tile: MultiTile<K, V>,
    vararg keys: K,
  ): Map<K, V> = get(tile, keys.toList())

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
  suspend inline fun <V> assertEquals(
    tile: Tile<V>,
    expected: V,
  ) = testAssertEquals(expected, get<V>(tile))

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
  suspend inline fun <V> assertEquals(
    tile: Tile<V>,
    expected: V,
    message: String,
  ) = testAssertEquals(expected, get(tile), message)

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
  suspend inline fun <K, V> assertEquals(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
    expected: Map<K, V>,
  ) = testAssertEquals(expected, get(tile, keys))

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
  suspend inline fun <K, V> assertEquals(
    tile: MultiTile<K, V>,
    keys: List<K>,
    expected: Map<K, V>,
    message: String,
  ) = testAssertEquals(expected, get(tile, keys), message)

  /**
   * Asserts that a [Tile] throws the expected exception when its [Tile.get] is called.
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
  suspend inline fun assertThrows(
    tile: Tile<*>,
    expectedException: KClass<out Throwable>,
  ) = testAssertFailsWith(expectedException) { get(tile) }

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
  suspend inline fun assertThrows(
    tile: Tile<*>,
    expectedException: KClass<out Throwable>,
    message: String,
  ) = testAssertFailsWith(expectedException, message) { get(tile) }

  /**
   * Asserts that a [MultiTile] throws the expected exception when [MultiTile.getByKeys] is called.
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
  suspend inline fun <K> assertThrows(
    tile: MultiTile<K, *>,
    keys: List<K>,
    expectedException: KClass<out Throwable>,
  ) = testAssertFailsWith(expectedException) { get(tile, keys) }
}
