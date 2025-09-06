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

package org.buildmosaic.core

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@Suppress("FunctionOnlyReturningConstant", "FunctionMaxLength")
class MosaicTest {
  private lateinit var registry: MosaicRegistry
  private lateinit var mosaic: Mosaic

  @BeforeTest
  fun setUp() {
    registry = MosaicRegistry()
    mosaic = Mosaic(registry, TestRequest())
  }

  @Test
  fun `should create new tile instance on first call`() {
    registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }
    registry.register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }

    val tile = mosaic.getTile<TestSingleTile>()
    val tile2 = mosaic.getTile<TestMultiTile>()

    assertIs<TestSingleTile>(tile)
    assertIs<TestMultiTile>(tile2)
  }

  @Test
  fun `should return cached tile on subsequent calls`() {
    var constructorCallCount = 0
    registry.register(TestSingleTile::class) { mosaic ->
      constructorCallCount++
      TestSingleTile(mosaic)
    }

    val tile1 = mosaic.getTile<TestSingleTile>()
    val tile2 = mosaic.getTile<TestSingleTile>()
    val tile3 = mosaic.getTile<TestSingleTile>()

    assertEquals(1, constructorCallCount)
    assertSame(tile1, tile2)
    assertSame(tile1, tile3)
  }

  @Test
  fun `should handle different tile types separately`() {
    var singleTileCallCount = 0
    var multiTileCallCount = 0

    registry.register(TestSingleTile::class) { mosaic ->
      singleTileCallCount++
      TestSingleTile(mosaic)
    }
    registry.register(TestMultiTile::class) { mosaic ->
      multiTileCallCount++
      TestMultiTile(mosaic)
    }

    val singleTile1 = mosaic.getTile<TestSingleTile>()
    val singleTile2 = mosaic.getTile<TestSingleTile>()
    val multiTile1 = mosaic.getTile<TestMultiTile>()
    val multiTile2 = mosaic.getTile<TestMultiTile>()

    assertEquals(1, singleTileCallCount)
    assertEquals(1, multiTileCallCount)
    assertSame(singleTile1, singleTile2)
    assertSame(multiTile1, multiTile2)
    assertNotSame<Tile>(singleTile1, multiTile1)
  }

  @Test
  fun `should work with non-reified getTile method`() {
    registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }
    registry.register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }

    val tile1 = mosaic.getTile(TestSingleTile::class)
    val tile2 = mosaic.getTile<TestSingleTile>()
    val tile3 = mosaic.getTile(TestMultiTile::class)
    val tile4 = mosaic.getTile<TestMultiTile>()

    assertSame(tile1, tile2)
    assertSame(tile3, tile4)
  }

  @Test
  fun `should handle multiple mosaic instances independently`() {
    val mosaic1 = Mosaic(registry, TestRequest())
    val mosaic2 = Mosaic(registry, TestRequest())

    registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

    val tile1 = mosaic1.getTile<TestSingleTile>()
    val tile2 = mosaic2.getTile<TestSingleTile>()

    assertNotSame(tile1, tile2)
  }

  @Test
  fun `should throw exception for unregistered tile type`() {
    assertFailsWith<IllegalArgumentException> { mosaic.getTile<TestSingleTile>() }
  }

  @Test
  fun `should cache tiles across different access patterns`() {
    var callCount = 0
    registry.register(TestSingleTile::class) { mosaic ->
      callCount++
      TestSingleTile(mosaic)
    }

    // Access via inline method
    val tile1 = mosaic.getTile<TestSingleTile>()
    // Access via non-inline method
    val tile2 = mosaic.getTile(TestSingleTile::class)
    // Access via inline method again
    val tile3 = mosaic.getTile<TestSingleTile>()

    assertEquals(1, callCount)
    assertSame(tile1, tile2)
    assertSame(tile1, tile3)
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
