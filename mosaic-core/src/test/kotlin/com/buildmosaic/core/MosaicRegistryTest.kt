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

package com.buildmosaic.core

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Suppress("FunctionOnlyReturningConstant", "FunctionMaxLength")
class MosaicRegistryTest {
  private lateinit var registry: MosaicRegistry
  private lateinit var mosaic: Mosaic

  @BeforeTest
  fun setUp() {
    registry = MosaicRegistry()
    mosaic = Mosaic(registry, TestRequest())
  }

  @Test
  fun `should register and retrieve tile constructor`() {
    var constructorCalled = false
    val testTile = TestSingleTile(mosaic)

    registry.register(TestSingleTile::class) { _ ->
      constructorCalled = true
      testTile
    }

    val result = registry.getInstance(TestSingleTile::class, mosaic)

    assertTrue(constructorCalled)
    assertSame(testTile, result)
  }

  @Test
  fun `should throw exception for unregistered tile class`() {
    assertFailsWith<IllegalArgumentException> {
      registry.getInstance(TestSingleTile::class, mosaic)
    }
  }

  @Test
  fun `should handle multiple tile registrations`() {
    val singleTile = TestSingleTile(mosaic)
    val multiTile = TestMultiTile(mosaic)

    registry.register(TestSingleTile::class) { _ -> singleTile }
    registry.register(TestMultiTile::class) { _ -> multiTile }

    val result1 = registry.getInstance(TestSingleTile::class, mosaic)
    val result2 = registry.getInstance(TestMultiTile::class, mosaic)

    assertSame(singleTile, result1)
    assertSame(multiTile, result2)
  }

  @Test
  fun `should override existing registration`() {
    val firstTile = TestSingleTile(mosaic)
    val secondTile = TestSingleTile(mosaic)

    registry.register(TestSingleTile::class) { _ -> firstTile }
    registry.register(TestSingleTile::class) { _ -> secondTile }

    val result = registry.getInstance(TestSingleTile::class, mosaic)

    assertSame(secondTile, result)
    assertNotSame(firstTile, result)
  }

  @Test
  fun `should pass mosaic instance to constructor`() {
    var receivedMosaic: Mosaic? = null

    registry.register(TestSingleTile::class) { mosaic ->
      receivedMosaic = mosaic
      TestSingleTile(mosaic)
    }

    registry.getInstance(TestSingleTile::class, mosaic)

    assertSame(mosaic, receivedMosaic)
  }

  private class TestSingleTile(mosaic: Mosaic) : SingleTile<String>(mosaic) {
    override suspend fun retrieve(): String = "test-value"
  }

  private class TestMultiTile(mosaic: Mosaic) : MultiTile<String, List<String>>(mosaic) {
    override suspend fun retrieveForKeys(keys: List<String>): List<String> = keys.map { "value-$it" }

    override fun normalize(
      key: String,
      response: List<String>,
    ): String = "normalized-$key"
  }

  private class TestRequest : MosaicRequest {
    // Simple test implementation
  }
}
