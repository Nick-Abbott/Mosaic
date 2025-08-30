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

package com.abbott.mosaic.test

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.MosaicRegistry
import com.abbott.mosaic.MosaicRequest
import com.abbott.mosaic.MultiTile
import com.abbott.mosaic.SingleTile
import com.abbott.mosaic.Tile
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

/**
 * Builder for creating test mosaics with mocked tiles.
 * Provides a fluent API for setting up test scenarios.
 */
class TestMosaicBuilder {
  private val internalRegistry = MosaicRegistry()
  private var request: MosaicRequest = MockMosaicRequest()
  private val mockTiles = mutableMapOf<KClass<*>, Tile>()

  /**
   * Adds a mock tile that returns the specified data.
   *
   * @param tileClass The class of the tile to mock
   * @param returnData The data the tile should return
   * @param behavior The behavior of the mock tile
   * @return This builder for method chaining
   */
  fun <T : Tile, R> withMockTile(
    tileClass: KClass<T>,
    returnData: R,
    behavior: MockBehavior = MockBehavior.SUCCESS,
  ): TestMosaicBuilder {
    val mockTile = createMockTileWithMockK(tileClass, returnData, behavior)
    mockTiles[tileClass] = mockTile
    return this
  }

  fun <T : SingleTile<R>, R> withMockTile(
    tileClass: KClass<T>,
    returnData: R,
    behavior: MockBehavior = MockBehavior.SUCCESS,
  ): TestMosaicBuilder {
    val mockTile = createMockTileWithMockK(tileClass, returnData, behavior)
    mockTiles[tileClass] = mockTile
    return this
  }

  inline fun <reified T : SingleTile<R>, R> withMockTile(
    returnData: R,
    behavior: MockBehavior = MockBehavior.SUCCESS,
  ): TestMosaicBuilder {
    return withMockTile(T::class, returnData, behavior)
  }

  fun <T : SingleTile<R>, R> withMockTileDelay(
    tileClass: KClass<T>,
    returnData: R,
  ): TestMosaicBuilder {
    val mockTile = createMockTileWithMockK(tileClass, returnData, MockBehavior.DELAY)
    mockTiles[tileClass] = mockTile
    return this
  }

  inline fun <reified T : SingleTile<R>, R> withMockTileDelay(
    returnData: R,
  ): TestMosaicBuilder {
    return withMockTileDelay(T::class, returnData)
  }

  fun <T : SingleTile<R>, R> withMockTileError(
    tileClass: KClass<T>,
    error: Throwable,
  ): TestMosaicBuilder {
    val mockTile = createMockTileWithMockK<T, R>(tileClass, returnData = null as R, behavior = MockBehavior.ERROR, error = error)
    mockTiles[tileClass] = mockTile
    return this
  }

  inline fun <reified T : SingleTile<R>, R> withMockTileError(
    error: Throwable,
  ): TestMosaicBuilder {
    return withMockTileError<T, R>(T::class, error)
  }

  fun <T : SingleTile<R>, R> withMockTileCustom(
    tileClass: KClass<T>,
    custom: suspend () -> R,
  ): TestMosaicBuilder {
    val mockTile = createMockTileWithMockK<T, R>(tileClass, returnData = null as R, behavior = MockBehavior.CUSTOM, custom = custom)
    mockTiles[tileClass] = mockTile
    return this
  }

  inline fun <reified T : SingleTile<R>, R> withMockTileCustom(
    noinline custom: suspend () -> R,
  ): TestMosaicBuilder {
    return withMockTileCustom<T, R>(T::class, custom)
  }


  /**
   * Adds a mock multi-tile that returns the specified data.
   *
   * @param tileClass The class of the tile to mock
   * @param returnData The map of data the tile should return
   * @param behavior The behavior of the mock tile
   * @return This builder for method chaining
   */
  fun <T : Tile, R> withMockMultiTile(
    tileClass: KClass<T>,
    returnData: Map<String, R>,
    behavior: MockBehavior = MockBehavior.SUCCESS,
  ): TestMosaicBuilder {
    val mockTile = createMockTileWithMockK(tileClass, returnData, behavior)
    mockTiles[tileClass] = mockTile
    return this
  }

  fun <T : MultiTile<*, R>, R> withMockMultiTile(
    tileClass: KClass<T>,
    returnData: Map<String, R>,
    behavior: MockBehavior = MockBehavior.SUCCESS,
  ): TestMosaicBuilder {
    val mockTile = createMockTileWithMockK(tileClass, returnData, behavior)
    mockTiles[tileClass] = mockTile
    return this
  }

  inline fun <reified T : MultiTile<*, R>, R> withMockMultiTile(
    returnData: Map<String, R>,
    behavior: MockBehavior = MockBehavior.SUCCESS,
  ): TestMosaicBuilder {
    return withMockMultiTile(T::class, returnData, behavior)
  }

  fun <T : MultiTile<*, R>, R> withMockMultiTileDelay(
    tileClass: KClass<T>,
    returnData: Map<String, R>,
  ): TestMosaicBuilder {
    val mockTile = createMockTileWithMockK(tileClass, returnData, MockBehavior.DELAY)
    mockTiles[tileClass] = mockTile
    return this
  }

  inline fun <reified T : MultiTile<*, R>, R> withMockMultiTileDelay(
    returnData: Map<String, R>,
  ): TestMosaicBuilder {
    return withMockMultiTileDelay(T::class, returnData)
  }

  fun <T : MultiTile<*, R>, R> withMockMultiTileError(
    tileClass: KClass<T>,
    error: Throwable,
  ): TestMosaicBuilder {
    val mockTile = createMockTileWithMockK<T, Map<String, R>>(tileClass, returnData = emptyMap(), behavior = MockBehavior.ERROR, error = error)
    mockTiles[tileClass] = mockTile
    return this
  }

  inline fun <reified T : MultiTile<*, R>, R> withMockMultiTileError(
    error: Throwable,
  ): TestMosaicBuilder {
    return withMockMultiTileError<T, R>(T::class, error)
  }

  fun <T : MultiTile<*, R>, R> withMockMultiTileCustom(
    tileClass: KClass<T>,
    custom: suspend (keys: List<String>) -> Map<String, R>,
  ): TestMosaicBuilder {
    val mockTile = createMockTileWithMockK<T, Map<String, R>>(tileClass, returnData = emptyMap(), behavior = MockBehavior.CUSTOM, custom = custom)
    mockTiles[tileClass] = mockTile
    return this
  }

  inline fun <reified T : MultiTile<*, R>, R> withMockMultiTileCustom(
    noinline custom: suspend (keys: List<String>) -> Map<String, R>,
  ): TestMosaicBuilder {
    return withMockMultiTileCustom<T, R>(T::class, custom)
  }


  /**
   * Sets the request to use for the test mosaic.
   *
   * @param request The request to use
   * @return This builder for method chaining
   */
  fun withRequest(request: MosaicRequest): TestMosaicBuilder {
    this.request = request
    return this
  }

  /**
   * Builds and returns a TestMosaic instance.
   *
   * @return A TestMosaic with the configured mocks and request
   */
  fun build(): TestMosaic {
    runCatching {
      Class
        .forName("com.abbott.mosaic.generated.GeneratedMosaicRegistryKt")
        .getMethod("registerGeneratedTiles", MosaicRegistry::class.java)
        .invoke(null, internalRegistry)
    }

    @Suppress("UNCHECKED_CAST")
    mockTiles.forEach { (clazz, tile) ->
      internalRegistry.register(clazz as KClass<Tile>) { tile }
    }

    val mosaic = Mosaic(internalRegistry, request)
    return TestMosaic(mosaic, mockTiles)
  }

  private fun <T : Tile, R> createMockTileWithMockK(
    tileClass: KClass<T>,
    returnData: R,
    behavior: MockBehavior,
    error: Throwable? = null,
    custom: Any? = null,
  ): T {
    val mock = mockkClass(tileClass)
    setupMockBehavior(mock, tileClass, returnData, behavior, error, custom)
    return mock
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : Tile, R> setupMockBehavior(
    mock: T,
    tileClass: KClass<T>,
    returnData: R,
    behavior: MockBehavior,
    error: Throwable?,
    custom: Any?,
  ) {
    when {
      SingleTile::class.java.isAssignableFrom(tileClass.java) -> {
        setupSingleTileMock(
          mock as SingleTile<R>,
          returnData,
          behavior,
          error,
          custom as (suspend () -> R)?,
        )
      }
      MultiTile::class.java.isAssignableFrom(tileClass.java) -> {
        setupMultiTileMock(
          mock as MultiTile<*, R>,
          returnData as Map<String, R>,
          behavior,
          error,
          custom as (suspend (List<String>) -> Map<String, R>)?,
        )
      }
    }
  }

  private fun <R> setupSingleTileMock(
    mock: SingleTile<R>,
    returnData: R,
    behavior: MockBehavior,
    error: Throwable?,
    custom: (suspend () -> R)?,
  ) {
    coEvery { mock.get() } answers {
      runBlocking {
        when (behavior) {
          MockBehavior.SUCCESS -> returnData
          MockBehavior.ERROR -> throw (error ?: RuntimeException("Mock error"))
          MockBehavior.DELAY -> {
            delay(100)
            returnData
          }
          MockBehavior.CUSTOM -> custom?.invoke() ?: returnData
        }
      }
    }
  }

  private fun <R> setupMultiTileMock(
    mock: MultiTile<*, R>,
    returnData: Map<String, R>,
    behavior: MockBehavior,
    error: Throwable?,
    custom: (suspend (List<String>) -> Map<String, R>)?,
  ) {
    coEvery { mock.getByKeys(any()) } answers {
      val keys = this.args[0] as List<String>
      runBlocking {
        when (behavior) {
          MockBehavior.SUCCESS -> returnData.filterKeys { it in keys }
          MockBehavior.ERROR -> throw (error ?: RuntimeException("Mock error"))
          MockBehavior.DELAY -> {
            delay(100)
            returnData.filterKeys { it in keys }
          }
          MockBehavior.CUSTOM -> custom?.invoke(keys) ?: returnData.filterKeys { it in keys }
        }
      }
    }
  }
}
