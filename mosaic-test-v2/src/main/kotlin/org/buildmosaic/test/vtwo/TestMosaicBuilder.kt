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

import io.mockk.coEvery
import io.mockk.slot
import io.mockk.spyk
import kotlinx.coroutines.delay
import org.buildmosaic.core.vtwo.Mosaic
import org.buildmosaic.core.vtwo.MosaicRequest
import org.buildmosaic.core.vtwo.MultiTile
import org.buildmosaic.core.vtwo.Tile
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
 *   .withMockTile(MyTile, "test data")
 *   .withFailedTile(OtherTile, RuntimeException("Test error"))
 *   .withDelayedTile(SlowTile, "delayed data", 1000) // 1 second delay
 *   .build()
 * ```
 *
 * ### MultiTile Usage
 * ```kotlin
 * val testMosaic = TestMosaicBuilder()
 *   .withMockTile(UserTile, mapOf("user1" to user1, "user2" to user2))
 *   .withCustomTile(ProfileTile) { keys ->
 *     // Custom logic based on requested keys
 *     keys.associateWith { key -> createMockProfile(key) }
 *   }
 *   .build()
 * ```
 */
@Suppress("LargeClass")
class TestMosaicBuilder {
  private val injector = MockInjector()
  private var request: MosaicRequest = MosaicRequest()

  /**
   * Adds a mock [SingleTile] that returns the specified response.
   *
   * @param V The type of data the tile returns
   * @param tile The [Tile] to mock
   * @param response The response to return when the tile's [SingleTile.get] is called
   * @return This builder for method chaining
   *
   * ```kotlin
   * val testMosaic = TestMosaicBuilder()
   *   .withMockTile(MyTile, "test data")
   *   .build()
   * ```
   */
  fun <V> withMockTile(
    tile: Tile<V>,
    response: V,
  ): TestMosaicBuilder {
    val spy = spyk(tile, recordPrivateCalls = true)
    coEvery { spy["block"](any()) } coAnswers { response }
    return this
  }

  /**
   * Adds a mock [SingleTile] that fails with the specified exception.
   *
   * @param tile The [Tile] to mock
   * @param throwable The exception to throw when the tile's [SingleTile.get] is called
   * @return This builder for method chaining
   *
   * ```kotlin
   * val testMosaic = TestMosaicBuilder()
   *   .withFailedTile(MyTile, RuntimeException("Test error"))
   *   .build()
   * ```
   */
  fun withFailedTile(
    tile: Tile<*>,
    throwable: Throwable,
  ): TestMosaicBuilder {
    val spy = spyk(tile, recordPrivateCalls = true)
    coEvery { spy["block"](any()) } coAnswers { throw throwable }
    return this
  }

  /**
   * Adds a mock [SingleTile] that delays before returning the response.
   *
   * @param V The type of data the tile returns
   * @param tile The [Tile] to mock
   * @param response The response to return after the delay
   * @param delayMs The delay in milliseconds before returning the response
   * @return This builder for method chaining
   *
   * ```kotlin
   * val testMosaic = TestMosaicBuilder()
   *   .withDelayedTile(MyTile, "delayed data", 1000) // 1 second delay
   *   .build()
   * ```
   */
  fun <V> withDelayedTile(
    tile: Tile<V>,
    response: V,
    delayMs: Long,
  ): TestMosaicBuilder {
    val spy = spyk(tile, recordPrivateCalls = true)
    coEvery { spy["block"](any()) } coAnswers {
      delay(delayMs)
      response
    }
    return this
  }

  /**
   * Adds a mock [SingleTile] with custom behavior.
   *
   * @param V The type of data the tile returns
   * @param tile The [Tile] to mock
   * @param provider A suspending lambda that provides the response
   * @return This builder for method chaining
   *
   * ```kotlin
   * val testMosaic = TestMosaicBuilder()
   *   .withCustomTile(MyTile) {
   *     // Custom logic here
   *     if (condition) "result1" else "result2"
   *   }
   *   .build()
   * ```
   */
  fun <V> withCustomTile(
    tile: Tile<V>,
    provider: suspend Mosaic.() -> V,
  ): TestMosaicBuilder {
    val spy = spyk(tile, recordPrivateCalls = true)
    val inputMosaic = slot<Mosaic>()
    coEvery { spy["block"](capture(inputMosaic)) } coAnswers { provider(inputMosaic.captured) }
    return this
  }

  /**
   * Adds a mock [MultiTile] that returns the specified responses for given keys.
   *
   * @param K The type of keys in the response
   * @param V The type of individual values in the response
   * @param tile The [Tile] to mock
   * @param response Map of keys to their corresponding values
   * @return This builder for method chaining
   *
   * ```kotlin
   * val testMosaic = TestMosaicBuilder()
   *   .withMockTile(UserTile, mapOf(
   *     "user1" to User("user1"),
   *     "user2" to User("user2")
   *   ))
   *   .build()
   * ```
   */
  @JvmName("withMockMultiTile")
  fun <K, V> withMockTile(
    tile: MultiTile<K, V>,
    response: Map<K, V>,
  ): TestMosaicBuilder {
    val spy = spyk(tile, recordPrivateCalls = true)
    val keys = slot<Set<K>>()
    coEvery { spy["block"](any(), capture(keys)) } coAnswers {
      response.filterKeys { it in keys.captured }
    }
    return this
  }

  /**
   * Adds a mock [MultiTile] that fails with the specified exception.
   *
   * @param K The type of keys in the response
   * @param tile The [Tile] to mock
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
  fun <K> withFailedTile(
    tile: MultiTile<K, *>,
    throwable: Throwable,
  ): TestMosaicBuilder {
    val spy = spyk(tile, recordPrivateCalls = true)
    coEvery { spy["block"](any(), any<Set<K>>()) } coAnswers { throw throwable }
    return this
  }

  /**
   * Adds a mock [MultiTile] that delays before returning responses.
   *
   * @param K The type of keys in the response
   * @param V The type of values in the response
   * @param tile The [MultiTile] to mock
   * @param response Map of keys to their corresponding values
   * @param delayMs The delay in milliseconds before returning the response
   * @return This builder for method chaining
   *
   * ```kotlin
   * val testMosaic = TestMosaicBuilder()
   *   .withDelayedTile(UserTile, mapOf("user1" to User("user1")), 500)
   *   .build()
   * ```
   */
  @JvmName("withDelayedMultiTile")
  fun <K, V> withDelayedTile(
    tile: MultiTile<K, V>,
    response: Map<K, V>,
    delayMs: Long,
  ): TestMosaicBuilder {
    val spy = spyk(tile, recordPrivateCalls = true)
    val keys = slot<Set<K>>()
    coEvery { spy["block"](any(), capture(keys)) } coAnswers {
      delay(delayMs)
      response.filterKeys { it in keys.captured }
    }
    return this
  }

  /**
   * Adds a mock [MultiTile] with custom behavior.
   *
   * @param K The type of keys in the response
   * @param V The type of values in the response
   * @param tile The [MultiTile] to mock
   * @param provider A suspending lambda that provides responses based on requested keys
   * @return This builder for method chaining
   *
   * ```kotlin
   * val testMosaic = TestMosaicBuilder()
   *   .withCustomTile(UserTile) { keys ->
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
  fun <K, V> withCustomTile(
    tile: MultiTile<K, V>,
    provider: suspend Mosaic.(Set<K>) -> Map<K, V>,
  ): TestMosaicBuilder {
    val spy = spyk(tile, recordPrivateCalls = true)
    val inputMosaic = slot<Mosaic>()
    val keys = slot<Set<K>>()
    coEvery { spy["block"](capture(inputMosaic), capture(keys)) } coAnswers {
      provider(inputMosaic.captured, keys.captured)
    }
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

  fun <T : Any> withInjection(
    clazz: KClass<T>,
    obj: T,
  ): TestMosaicBuilder {
    injector.register(clazz, obj)
    return this
  }

  inline fun <reified T : Any> withInjection(obj: T): TestMosaicBuilder = withInjection(T::class, obj)

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
  fun build(): TestMosaic = TestMosaic(Mosaic(request, injector))
}
