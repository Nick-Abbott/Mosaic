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
import com.buildmosaic.core.MultiTile
import com.buildmosaic.core.SingleTile
import com.buildmosaic.core.Tile
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach

/**
 * Base class for mock tile testing with common utilities.
 */
abstract class MockTileTest {
  protected lateinit var mosaic: Mosaic

  @BeforeEach
  fun setUp() {
    mosaic = mockk<Mosaic>()
  }

  /**
   * Helper function to run tests with coroutines.
   */
  protected fun runMockTileTest(block: suspend () -> Unit) =
    runTest {
      block()
    }

  /**
   * Helper function to create a mock SingleTile.
   */
  protected inline fun <reified T : SingleTile<R>, R> createMockSingleTile(): T {
    return mockk<T>()
  }

  /**
   * Helper function to create a mock MultiTile.
   */
  protected inline fun <reified T : MultiTile<R, S>, R, S> createMockMultiTile(): T {
    return mockk<T>()
  }

  /**
   * Helper function to create a mock Tile.
   */
  protected inline fun <reified T : Tile> createMockTile(): T {
    return mockk<T>()
  }
}
