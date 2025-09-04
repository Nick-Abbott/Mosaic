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
import com.buildmosaic.core.Tile
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import kotlin.reflect.KClass

/**
 * Base test class for TestMosaic testing with common setup and utilities.
 */
abstract class BaseTestMosaicTest {
  protected lateinit var registry: MosaicRegistry
  protected lateinit var request: MosaicRequest
  protected lateinit var mosaic: Mosaic
  protected lateinit var testMosaic: TestMosaic

  @BeforeEach
  fun setUp() {
    registry = MosaicRegistry()
    request = MockMosaicRequest()
    mosaic = Mosaic(registry, request)
    testMosaic = TestMosaic(mosaic, emptyMap())
  }

  /**
   * Helper function to run tests with coroutines.
   */
  protected fun runTestMosaicTest(block: suspend () -> Unit) =
    runTest {
      block()
    }

  /**
   * Helper function to create a TestMosaic with mocked tiles.
   */
  protected fun createTestMosaicWithMocks(mockTiles: Map<KClass<*>, Tile>): TestMosaic {
    return TestMosaic(mosaic, mockTiles)
  }
}
