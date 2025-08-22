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

    internalRegistry.register(tileClass) { _ ->
      mockTile
    }
    return this
  }

  /**
   * Adds a mock tile that returns the specified data (reified version).
   *
   * @param returnData The data the tile should return
   * @param behavior The behavior of the mock tile
   * @return This builder for method chaining
   */
  inline fun <reified T : Tile, R> withMockTile(
    returnData: R,
    behavior: MockBehavior = MockBehavior.SUCCESS,
  ): TestMosaicBuilder {
    return withMockTile(T::class, returnData, behavior)
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

    internalRegistry.register(tileClass) { _ ->
      mockTile
    }
    return this
  }

  /**
   * Adds a mock multi-tile that returns the specified data (reified version).
   *
   * @param returnData The map of data the tile should return
   * @param behavior The behavior of the mock tile
   * @return This builder for method chaining
   */
  inline fun <reified T : Tile, R> withMockMultiTile(
    returnData: Map<String, R>,
    behavior: MockBehavior = MockBehavior.SUCCESS,
  ): TestMosaicBuilder {
    return withMockMultiTile(T::class, returnData, behavior)
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
    val mosaic = Mosaic(internalRegistry, request)
    return TestMosaic(mosaic, mockTiles)
  }

  private fun <T : Tile, R> createMockTileWithMockK(
    tileClass: KClass<T>,
    returnData: R,
    behavior: MockBehavior,
  ): T {
    val mock = mockkClass(tileClass)
    setupMockBehavior(mock, tileClass, returnData, behavior)
    return mock
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : Tile, R> setupMockBehavior(
    mock: T,
    tileClass: KClass<T>,
    returnData: R,
    behavior: MockBehavior,
  ) {
    when {
      SingleTile::class.java.isAssignableFrom(tileClass.java) -> {
        setupSingleTileMock(mock as SingleTile<R>, returnData, behavior)
      }
      MultiTile::class.java.isAssignableFrom(tileClass.java) -> {
        setupMultiTileMock(mock as MultiTile<*, *>, returnData as Map<String, Any>, behavior)
      }
    }
  }

  private fun <R> setupSingleTileMock(
    mock: SingleTile<R>,
    returnData: R,
    behavior: MockBehavior,
  ) {
    coEvery { mock.get() } answers {
      runBlocking {
        when (behavior) {
          MockBehavior.SUCCESS -> returnData
          MockBehavior.ERROR -> throw RuntimeException("Mock error")
          MockBehavior.DELAY -> {
            delay(100)
            returnData
          }
          MockBehavior.CUSTOM -> returnData
        }
      }
    }
  }

  private fun setupMultiTileMock(
    mock: MultiTile<*, *>,
    returnData: Map<String, Any>,
    behavior: MockBehavior,
  ) {
    coEvery { mock.getByKeys(any<List<String>>()) } answers {
      val keys = firstArg<List<String>>()
      runBlocking {
        when (behavior) {
          MockBehavior.SUCCESS -> returnData.filterKeys { it in keys }
          MockBehavior.ERROR -> throw RuntimeException("Mock error")
          MockBehavior.DELAY -> {
            delay(100)
            returnData.filterKeys { it in keys }
          }
          MockBehavior.CUSTOM -> returnData.filterKeys { it in keys }
        }
      }
    }
  }
}
