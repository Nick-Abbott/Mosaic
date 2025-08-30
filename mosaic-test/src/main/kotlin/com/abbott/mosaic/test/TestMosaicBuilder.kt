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

@file:Suppress("LargeClass")

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
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

/**
 * Builder for creating test mosaics with mocked tiles.
 * Provides a fluent API for setting up test scenarios.
 */
class TestMosaicBuilder {
  private val internalRegistry = MosaicRegistry()
  private var request: MosaicRequest = MockMosaicRequest()
  private val mockTiles = mutableMapOf<KClass<*>, Tile>()

  // region SingleTile builders
  fun <R, T : SingleTile<R>> withMockTile(
    tileClass: KClass<T>,
    response: R,
  ): TestMosaicBuilder {
    val mock = createSingleTileMock(tileClass, response, MockBehavior.SUCCESS)
    mockTiles[tileClass] = mock
    return this
  }

  inline fun <R, reified T : SingleTile<R>> withMockTile(response: R): TestMosaicBuilder =
    withMockTile(T::class, response)

  fun <R, T : SingleTile<R>> withFailedTile(
    tileClass: KClass<T>,
    throwable: Throwable,
  ): TestMosaicBuilder {
    val mock = createSingleTileMock(tileClass, null, MockBehavior.ERROR, throwable = throwable)
    mockTiles[tileClass] = mock
    return this
  }

  fun <R, T : SingleTile<R>> withDelayedTile(
    tileClass: KClass<T>,
    response: R,
    delayMs: Long,
  ): TestMosaicBuilder {
    val mock = createSingleTileMock(tileClass, response, MockBehavior.DELAY, delay = delayMs)
    mockTiles[tileClass] = mock
    return this
  }

  inline fun <R, reified T : SingleTile<R>> withDelayedTile(
    response: R,
    delayMs: Long,
  ): TestMosaicBuilder = withDelayedTile(T::class, response, delayMs)

  fun <R, T : SingleTile<R>> withCustomTile(
    tileClass: KClass<T>,
    provider: suspend () -> R,
  ): TestMosaicBuilder {
    val mock = createSingleTileMock(tileClass, null, MockBehavior.CUSTOM, custom = provider)
    mockTiles[tileClass] = mock
    return this
  }

  inline fun <R, reified T : SingleTile<R>> withCustomTile(noinline provider: suspend () -> R): TestMosaicBuilder =
    withCustomTile(T::class, provider)
  // endregion

  // region MultiTile builders (String keys)
  @JvmName("withMockMultiTile")
  fun <S, T : MultiTile<S, *>> withMockTile(
    tileClass: KClass<T>,
    response: Map<String, S>,
  ): TestMosaicBuilder {
    val mock = createMultiTileMock(tileClass, response, MockBehavior.SUCCESS)
    mockTiles[tileClass] = mock
    return this
  }

  @JvmName("withMockMultiTile")
  inline fun <S, reified T : MultiTile<S, *>> withMockTile(response: Map<String, S>): TestMosaicBuilder =
    withMockTile(T::class, response)

  @JvmName("withFailedMultiTile")
  fun <S, T : MultiTile<S, *>> withFailedTile(
    tileClass: KClass<T>,
    throwable: Throwable,
  ): TestMosaicBuilder {
    val mock = createMultiTileMock(tileClass, null, MockBehavior.ERROR, throwable = throwable)
    mockTiles[tileClass] = mock
    return this
  }

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

  @JvmName("withDelayedMultiTile")
  inline fun <S, reified T : MultiTile<S, *>> withDelayedTile(
    response: Map<String, S>,
    delayMs: Long,
  ): TestMosaicBuilder = withDelayedTile(T::class, response, delayMs)

  @JvmName("withCustomMultiTile")
  fun <S, T : MultiTile<S, *>> withCustomTile(
    tileClass: KClass<T>,
    provider: suspend (List<String>) -> Map<String, S>,
  ): TestMosaicBuilder {
    val mock = createMultiTileMock(tileClass, null, MockBehavior.CUSTOM, custom = provider)
    mockTiles[tileClass] = mock
    return this
  }

  @JvmName("withCustomMultiTile")
  inline fun <S, reified T : MultiTile<S, *>> withCustomTile(
    noinline provider: suspend (List<String>) -> Map<String, S>,
  ): TestMosaicBuilder = withCustomTile(T::class, provider)
  // endregion

  /**
   * Sets the request to use for the test mosaic.
   */
  fun withRequest(request: MosaicRequest): TestMosaicBuilder {
    this.request = request
    return this
  }

  /**
   * Builds and returns a TestMosaic instance.
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
    coEvery { mock.get() } answers {
      runBlocking {
        when (behavior) {
          MockBehavior.SUCCESS -> returnData!!
          MockBehavior.ERROR -> throw (throwable ?: RuntimeException("Mock error"))
          MockBehavior.DELAY -> {
            delay(delay)
            returnData!!
          }
          MockBehavior.CUSTOM -> custom!!.invoke()
        }
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
    coEvery { mock.getByKeys(any<List<String>>()) } answers {
      val keys = firstArg<List<String>>()
      runBlocking {
        when (behavior) {
          MockBehavior.SUCCESS -> returnData!!.filterKeys { it in keys }
          MockBehavior.ERROR -> throw (throwable ?: RuntimeException("Mock error"))
          MockBehavior.DELAY -> {
            delay(delay)
            returnData!!.filterKeys { it in keys }
          }
          MockBehavior.CUSTOM -> custom!!.invoke(keys)
        }
      }
    }
  }
}
