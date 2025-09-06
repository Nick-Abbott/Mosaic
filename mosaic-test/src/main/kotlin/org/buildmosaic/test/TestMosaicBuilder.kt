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

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.spyk
import kotlinx.coroutines.delay
import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.MosaicRegistry
import org.buildmosaic.core.MosaicRequest
import org.buildmosaic.core.MultiTile
import org.buildmosaic.core.SingleTile
import org.buildmosaic.core.Tile
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

/**
 * A builder for creating [TestMosaic] instances with mocked [Tile] implementations.
 *
 * This class provides a fluent API for setting up test scenarios by configuring mock tiles
 * with various behaviors. It supports both [SingleTile] and [MultiTile] mocks with
 * different behaviors like success, failure, delays, and custom logic.
 *
 * ### Basic Usage
 * ```kotlin
 * val testMosaic = TestMosaicBuilder()
 *   .withMockTile<MyTile>("test data")
 *   .withFailedTile<OtherTile>(RuntimeException("Test error"))
 *   .withDelayedTile<SlowTile>("delayed data", 1000) // 1 second delay
 *   .build()
 * ```
 *
 * ### MultiTile Usage
 * ```kotlin
 * val testMosaic = TestMosaicBuilder()
 *   .withMockTile<UserTile>(mapOf("user1" to user1, "user2" to user2))
 *   .withCustomTile<ProfileTile> { keys ->
 *     // Custom logic based on requested keys
 *     keys.associateWith { key -> createMockProfile(key) }
 *   }
 *   .build()
 * ```
 */
@Suppress("LargeClass")
class TestMosaicBuilder {
  private val internalRegistry = spyk(MosaicRegistry())
  private var request: MosaicRequest = MockMosaicRequest()
  private val mockTiles = mutableMapOf<KClass<*>, Tile>()

  /**
   * Adds a mock [SingleTile] that returns the specified response.
   *
   * @param R The type of data the tile returns
   * @param T The type of tile to mock
   * @param tileClass The [KClass] of the tile to mock
   * @param response The response to return when the tile's [SingleTile.get] is called
   * @return This builder for method chaining
   *
   * ```kotlin
   * val testMosaic = TestMosaicBuilder()
   *   .withMockTile(MyTile::class, "test data")
   *   .build()
   * ```
   */
  fun <R, T : SingleTile<R>> withMockTile(
    tileClass: KClass<T>,
    response: R,
  ): TestMosaicBuilder {
    val mock = createSingleTileMock(tileClass, response, MockBehavior.SUCCESS)
    mockTiles[tileClass] = mock
    return this
  }

  /**
   * Adds a mock [SingleTile] that fails with the specified exception.
   *
   * @param R The type of data the tile would return if successful
   * @param T The type of tile to mock
   * @param tileClass The [KClass] of the tile to mock
   * @param throwable The exception to throw when the tile's [SingleTile.get] is called
   * @return This builder for method chaining
   *
   * ```kotlin
   * val testMosaic = TestMosaicBuilder()
   *   .withFailedTile(MyTile::class, RuntimeException("Test error"))
   *   .build()
   * ```
   */
  fun <R, T : SingleTile<R>> withFailedTile(
    tileClass: KClass<T>,
    throwable: Throwable,
  ): TestMosaicBuilder {
    val mock = createSingleTileMock(tileClass, null, MockBehavior.ERROR, throwable = throwable)
    mockTiles[tileClass] = mock
    return this
  }

  /**
   * Adds a mock [SingleTile] that delays before returning the response.
   *
   * @param R The type of data the tile returns
   * @param T The type of tile to mock
   * @param tileClass The [KClass] of the tile to mock
   * @param response The response to return after the delay
   * @param delayMs The delay in milliseconds before returning the response
   * @return This builder for method chaining
   *
   * ```kotlin
   * val testMosaic = TestMosaicBuilder()
   *   .withDelayedTile(MyTile::class, "delayed data", 1000) // 1 second delay
   *   .build()
   * ```
   */
  fun <R, T : SingleTile<R>> withDelayedTile(
    tileClass: KClass<T>,
    response: R,
    delayMs: Long,
  ): TestMosaicBuilder {
    val mock = createSingleTileMock(tileClass, response, MockBehavior.DELAY, delay = delayMs)
    mockTiles[tileClass] = mock
    return this
  }

  /**
   * Adds a mock [SingleTile] with custom behavior.
   *
   * @param R The type of data the tile returns
   * @param T The type of tile to mock
   * @param tileClass The [KClass] of the tile to mock
   * @param provider A suspending lambda that provides the response
   * @return This builder for method chaining
   *
   * ```kotlin
   * val testMosaic = TestMosaicBuilder()
   *   .withCustomTile(MyTile::class) {
   *     // Custom logic here
   *     if (condition) "result1" else "result2"
   *   }
   *   .build()
   * ```
   */
  fun <R, T : SingleTile<R>> withCustomTile(
    tileClass: KClass<T>,
    provider: suspend () -> R,
  ): TestMosaicBuilder {
    val mock = createSingleTileMock(tileClass, null, MockBehavior.CUSTOM, custom = provider)
    mockTiles[tileClass] = mock
    return this
  }

  /**
   * Adds a mock [MultiTile] that returns the specified responses for given keys.
   *
   * @param S The type of individual values in the response
   * @param T The type of tile to mock
   * @param tileClass The [KClass] of the tile to mock
   * @param response Map of keys to their corresponding values
   * @return This builder for method chaining
   *
   * ```kotlin
   * val testMosaic = TestMosaicBuilder()
   *   .withMockTile(UserTile::class, mapOf(
   *     "user1" to User("user1"),
   *     "user2" to User("user2")
   *   ))
   *   .build()
   * ```
   */
  @JvmName("withMockMultiTile")
  fun <S, T : MultiTile<S, *>> withMockTile(
    tileClass: KClass<T>,
    response: Map<String, S>,
  ): TestMosaicBuilder {
    val mock = createMultiTileMock(tileClass, response, MockBehavior.SUCCESS)
    mockTiles[tileClass] = mock
    return this
  }

  /**
   * Adds a mock [MultiTile] that fails with the specified exception.
   *
   * @param S The type of individual values in the response
   * @param T The type of tile to mock
   * @param tileClass The [KClass] of the tile to mock
   * @param throwable The exception to throw when the tile's methods are called
   * @return This builder for method chaining
   *
   * ```kotlin
   * val testMosaic = TestMosaicBuilder()
   *   .withFailedTile(UserTile::class, RuntimeException("User not found"))
   *   .build()
   * ```
   */
  @JvmName("withFailedMultiTile")
  fun <S, T : MultiTile<S, *>> withFailedTile(
    tileClass: KClass<T>,
    throwable: Throwable,
  ): TestMosaicBuilder {
    val mock = createMultiTileMock(tileClass, null, MockBehavior.ERROR, throwable = throwable)
    mockTiles[tileClass] = mock
    return this
  }

  /**
   * Adds a mock [MultiTile] that delays before returning responses.
   *
   * @param S The type of individual values in the response
   * @param T The type of tile to mock
   * @param tileClass The [KClass] of the tile to mock
   * @param response Map of keys to their corresponding values
   * @param delayMs The delay in milliseconds before returning the response
   * @return This builder for method chaining
   *
   * ```kotlin
   * val testMosaic = TestMosaicBuilder()
   *   .withDelayedTile(UserTile::class, mapOf("user1" to User("user1")), 500)
   *   .build()
   * ```
   */
  @JvmName("withDelayedMultiTile")
  fun <S, T : MultiTile<S, *>> withDelayedTile(
    tileClass: KClass<T>,
    response: Map<String, S>,
    delayMs: Long,
  ): TestMosaicBuilder {
    val mock = createMultiTileMock(tileClass, response, MockBehavior.DELAY, delay = delayMs)
    mockTiles[tileClass] = mock
    return this
  }

  /**
   * Adds a mock [MultiTile] with custom behavior.
   *
   * @param S The type of individual values in the response
   * @param T The type of tile to mock
   * @param tileClass The [KClass] of the tile to mock
   * @param provider A suspending lambda that provides responses based on requested keys
   * @return This builder for method chaining
   *
   * ```kotlin
   * val testMosaic = TestMosaicBuilder()
   *   .withCustomTile(UserTile::class) { keys ->
   *     // Custom logic based on requested keys
   *     keys.associateWith { key ->
   *       if (key.startsWith("admin")) createAdminUser(key)
   *       else createRegularUser(key)
   *     }
   *   }
   *   .build()
   * ```
   */
  @JvmName("withCustomMultiTile")
  fun <S, T : MultiTile<S, *>> withCustomTile(
    tileClass: KClass<T>,
    provider: suspend (List<String>) -> Map<String, S>,
  ): TestMosaicBuilder {
    val mock = createMultiTileMock(tileClass, null, MockBehavior.CUSTOM, custom = provider)
    mockTiles[tileClass] = mock
    return this
  }

  /**
   * Sets the [MosaicRequest] to be used by the test mosaic.
   * A [MockMosaicRequest] is provided by default if not called.
   *
   * @param request The request instance to use
   * @return This builder for method chaining
   *
   * ```kotlin
   * val testMosaic = TestMosaicBuilder()
   *   .withRequest(MyCustomRequest())
   *   .withMockTile<MyTile>("test data")
   *   .build()
   * ```
   */
  fun withRequest(request: MosaicRequest): TestMosaicBuilder {
    this.request = request
    return this
  }

  /**
   * Builds and returns a configured [TestMosaic] instance.
   *
   * This method finalizes the builder configuration and creates a new [TestMosaic]
   * with all the specified mock tiles and request setup.
   *
   * @return A new [TestMosaic] instance ready for testing
   *
   * ```kotlin
   * val testMosaic = TestMosaicBuilder()
   *   .withMockTile<MyTile>("test data")
   *   .build()
   * ```
   */
  fun build(): TestMosaic {
    val mosaic = Mosaic(internalRegistry, request)
    every { internalRegistry.getInstance(any<KClass<out Tile>>(), any<Mosaic>()) } answers {
      runCatching { callOriginal() }.getOrElse {
        val clazz = firstArg<KClass<*>>().java
        val ctor = clazz.declaredConstructors.get(0)
        ctor.newInstance(mosaic) as Tile
      }
    }

    @Suppress("UNCHECKED_CAST")
    mockTiles.forEach { (clazz, tile) ->
      internalRegistry.register(clazz as KClass<Tile>) { tile }
    }

    return TestMosaic(mosaic)
  }

  private fun <R, T : SingleTile<R>> createSingleTileMock(
    tileClass: KClass<T>,
    returnData: R?,
    behavior: MockBehavior,
    throwable: Throwable? = null,
    delay: Long = 0,
    custom: (suspend () -> R)? = null,
  ): T {
    val mock = mockkClass(tileClass)
    setupSingleTileMock(mock, returnData, behavior, throwable, delay, custom)
    return mock
  }

  @Suppress("LongParameterList")
  private fun <R> setupSingleTileMock(
    mock: SingleTile<R>,
    returnData: R?,
    behavior: MockBehavior,
    throwable: Throwable?,
    delay: Long,
    custom: (suspend () -> R)?,
  ) {
    coEvery { mock.get() } coAnswers {
      when (behavior) {
        MockBehavior.SUCCESS -> returnData!!
        MockBehavior.ERROR -> throw throwable!!
        MockBehavior.DELAY -> {
          delay(delay)
          returnData!!
        }
        MockBehavior.CUSTOM -> custom!!.invoke()
      }
    }
  }

  private fun <S, T : MultiTile<S, *>> createMultiTileMock(
    tileClass: KClass<T>,
    returnData: Map<String, S>?,
    behavior: MockBehavior,
    throwable: Throwable? = null,
    delay: Long = 0,
    custom: (suspend (List<String>) -> Map<String, S>)? = null,
  ): T {
    val mock = mockkClass(tileClass)
    setupMultiTileMock(mock, returnData, behavior, throwable, delay, custom)
    return mock
  }

  @Suppress("LongParameterList")
  private fun <S> setupMultiTileMock(
    mock: MultiTile<S, *>,
    returnData: Map<String, S>?,
    behavior: MockBehavior,
    throwable: Throwable?,
    delay: Long,
    custom: (suspend (List<String>) -> Map<String, S>)?,
  ) {
    coEvery { mock.getByKeys(any<List<String>>()) } coAnswers {
      val keys = firstArg<List<String>>()
      when (behavior) {
        MockBehavior.SUCCESS -> returnData!!.filterKeys { it in keys }
        MockBehavior.ERROR -> throw throwable!!
        MockBehavior.DELAY -> {
          delay(delay)
          returnData!!.filterKeys { it in keys }
        }
        MockBehavior.CUSTOM -> custom!!.invoke(keys)
      }
    }
  }
}
