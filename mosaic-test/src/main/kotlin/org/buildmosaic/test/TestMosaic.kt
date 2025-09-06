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

import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.MosaicRequest
import org.buildmosaic.core.MultiTile
import org.buildmosaic.core.SingleTile
import org.buildmosaic.core.Tile
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
   * Retrieves an instance of the specified [Tile] class.
   *
   * @param T The type of tile to retrieve
   * @param tileClass The [KClass] of the tile to retrieve
   * @return An instance of the requested tile class
   * @throws IllegalArgumentException if the tile is not registered
   */
  fun <T : Tile> getTile(tileClass: KClass<T>): T = mosaic.getTile(tileClass)

  /**
   * Retrieves an instance of the specified [Tile] class using reified type parameters.
   *
   * This is a convenience method that infers the tile class from the type parameter.
   *
   * @param T The type of tile to retrieve (inferred from the return type)
   * @return An instance of the requested tile class
   * @throws IllegalArgumentException if the tile is not registered
   */
  inline fun <reified T : Tile> getTile(): T = getTile(T::class)

  /**
   * Asserts that a [SingleTile] returns the expected value.
   *
   * @param R The type of value the tile returns
   * @param tileClass The class of the tile to test
   * @param expected The expected value
   * @throws AssertionError if the actual value doesn't match the expected value
   *
   * ```kotlin
   * testMosaic.assertEquals(MyTile::class, "expected value")
   * ```
   */
  suspend fun <R> assertEquals(
    tileClass: KClass<out SingleTile<R>>,
    expected: R,
  ) {
    val tile = getTile(tileClass)
    val actual = tile.get()
    testAssertEquals(expected, actual)
  }

  /**
   * Asserts that a [SingleTile] returns the expected value with a custom failure message.
   *
   * @param R The type of value the tile returns
   * @param tileClass The class of the tile to test
   * @param expected The expected value
   * @param message The message to include in case of assertion failure
   * @throws AssertionError if the actual value doesn't match the expected value
   *
   * ```kotlin
   * testMosaic.assertEquals(
   *   tileClass = MyTile::class,
   *   expected = "expected value",
   *   message = "The tile did not return the expected value"
   * )
   * ```
   */
  suspend fun <R> assertEquals(
    tileClass: KClass<out SingleTile<R>>,
    expected: R,
    message: String,
  ) {
    val tile = getTile(tileClass)
    val actual = tile.get()
    testAssertEquals(expected, actual, message)
  }

  /**
   * Asserts that a [MultiTile] returns the expected values for the given keys.
   *
   * @param R The type of values in the response map
   * @param tileClass The class of the tile to test
   * @param keys The list of keys to request from the tile
   * @param expected The expected map of keys to values
   * @throws AssertionError if the actual values don't match the expected values
   *
   * ```kotlin
   * testMosaic.assertEquals(
   *   tileClass = UserTile::class,
   *   keys = listOf("user1", "user2"),
   *   expected = mapOf("user1" to User("user1"), "user2" to User("user2"))
   * )
   * ```
   */
  suspend fun <R> assertEquals(
    tileClass: KClass<out MultiTile<R, *>>,
    keys: List<String>,
    expected: Map<String, R>,
  ) {
    val tile = getTile(tileClass)
    val actual = tile.getByKeys(keys)
    testAssertEquals(expected, actual)
  }

  /**
   * Asserts that a [MultiTile] returns the expected values for the given keys with a custom failure message.
   *
   * @param R The type of values in the response map
   * @param tileClass The class of the tile to test
   * @param keys The list of keys to request from the tile
   * @param expected The expected map of keys to values
   * @param message The message to include in case of assertion failure
   * @throws AssertionError if the actual values don't match the expected values
   *
   * ```kotlin
   * testMosaic.assertEquals(
   *   tileClass = UserTile::class,
   *   keys = listOf("user1", "user2"),
   *   expected = mapOf("user1" to User("user1"), "user2" to User("user2")),
   *   message = "User data does not match expected values"
   * )
   * ```
   */
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

  /**
   * Asserts that a [SingleTile] throws the expected exception when its [SingleTile.get] is called.
   *
   * @param T The type of the tile
   * @param tileClass The class of the tile to test
   * @param expectedException The exception class that is expected to be thrown
   * @throws AssertionError if the tile does not throw the expected exception
   *
   * ```kotlin
   * testMosaic.assertThrows(
   *   tileClass = FailingTile::class,
   *   expectedException = IllegalStateException::class
   * )
   * ```
   */
  suspend fun <T : SingleTile<*>> assertThrows(
    tileClass: KClass<out T>,
    expectedException: KClass<out Throwable>,
  ) {
    val tile = getTile(tileClass)
    testAssertFailsWith(expectedException) { tile.get() }
  }

  /**
   * Asserts that a [SingleTile] throws the expected exception with a custom failure message.
   *
   * @param T The type of the tile
   * @param tileClass The class of the tile to test
   * @param expectedException The exception class that is expected to be thrown
   * @param message The message to include in case of assertion failure
   * @throws AssertionError if the tile does not throw the expected exception
   *
   * ```kotlin
   * testMosaic.assertThrows(
   *   tileClass = FailingTile::class,
   *   expectedException = IllegalStateException::class,
   *   message = "Expected IllegalStateException but got a different exception"
   * )
   * ```
   */
  suspend fun <T : SingleTile<*>> assertThrows(
    tileClass: KClass<out T>,
    expectedException: KClass<out Throwable>,
    message: String,
  ) {
    val tile = getTile(tileClass)
    testAssertFailsWith(expectedException, message) { tile.get() }
  }

  /**
   * Asserts that a [MultiTile] throws the expected exception when [MultiTile.getByKeys] is called.
   *
   * @param T The type of the tile
   * @param R The type of values in the response map
   * @param tileClass The class of the tile to test
   * @param keys The list of keys to request from the tile
   * @param expectedException The exception class that is expected to be thrown
   * @throws AssertionError if the tile does not throw the expected exception
   *
   * ```kotlin
   * testMosaic.assertThrows(
   *   tileClass = FailingUserTile::class,
   *   keys = listOf("user1", "user2"),
   *   expectedException = IllegalStateException::class
   * )
   * ```
   */
  suspend fun <T : MultiTile<R, *>, R> assertThrows(
    tileClass: KClass<out T>,
    keys: List<String>,
    expectedException: KClass<out Throwable>,
  ) {
    val tile = getTile(tileClass)
    testAssertFailsWith(expectedException) { tile.getByKeys(keys) }
  }

  /**
   * Asserts that a [MultiTile] throws the expected exception with a custom failure message.
   *
   * @param T The type of the tile
   * @param R The type of values in the response map
   * @param tileClass The class of the tile to test
   * @param keys The list of keys to request from the tile
   * @param expectedException The exception class that is expected to be thrown
   * @param message The message to include in case of assertion failure
   * @throws AssertionError if the tile does not throw the expected exception
   *
   * ```kotlin
   * testMosaic.assertThrows(
   *   tileClass = FailingUserTile::class,
   *   keys = listOf("user1", "user2"),
   *   expectedException = IllegalStateException::class,
   *   message = "Expected IllegalStateException but got a different exception"
   * )
   * ```
   */
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
