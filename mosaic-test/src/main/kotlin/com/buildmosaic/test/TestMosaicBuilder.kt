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
import com.buildmosaic.core.MosaicRegistry
import com.buildmosaic.core.MosaicRequest
import com.buildmosaic.core.MultiTile
import com.buildmosaic.core.SingleTile
import com.buildmosaic.core.Tile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.spyk
import kotlinx.coroutines.delay
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

/**
 * Builder for creating test mosaics with mocked tiles.
 * Provides a fluent API for setting up test scenarios.
 */
@Suppress("LargeClass")
class TestMosaicBuilder {
  private val internalRegistry = spyk(MosaicRegistry())
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

  fun <R, T : SingleTile<R>> withCustomTile(
    tileClass: KClass<T>,
    provider: suspend () -> R,
  ): TestMosaicBuilder {
    val mock = createSingleTileMock(tileClass, null, MockBehavior.CUSTOM, custom = provider)
    mockTiles[tileClass] = mock
    return this
  }

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

  @JvmName("withCustomMultiTile")
  fun <S, T : MultiTile<S, *>> withCustomTile(
    tileClass: KClass<T>,
    provider: suspend (List<String>) -> Map<String, S>,
  ): TestMosaicBuilder {
    val mock = createMultiTileMock(tileClass, null, MockBehavior.CUSTOM, custom = provider)
    mockTiles[tileClass] = mock
    return this
  }

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
