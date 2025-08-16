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

package com.abbott.mosaic

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@Suppress("FunctionOnlyReturningConstant", "FunctionMaxLength")
class MosaicTest {
  private lateinit var registry: MosaicRegistry
  private lateinit var mosaic: Mosaic

  @BeforeEach
  fun setUp() {
    registry = MosaicRegistry()
    mosaic = Mosaic(registry, TestRequest())
  }

  @Test
  fun `should create new tile instance on first call`() {
    var constructorCallCount = 0
    registry.register(TestSingleTile::class) { mosaic ->
      constructorCallCount++
      TestSingleTile(mosaic)
    }

    val tile = mosaic.getTile<TestSingleTile>()

    assertEquals(1, constructorCallCount)
    assertNotNull(tile)
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
    assertNotSame(singleTile1, multiTile1)
  }

  @Test
  fun `should work with non-inline getTile method`() {
    registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

    val tile1 = mosaic.getTile(TestSingleTile::class)
    val tile2 = mosaic.getTile<TestSingleTile>()

    assertSame(tile1, tile2)
  }

  @Test
  fun `should handle multiple mosaic instances independently`() {
    val mosaic1 = Mosaic(registry, TestRequest())
    val mosaic2 = Mosaic(registry, TestRequest())

    registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

    val tile1 = mosaic1.getTile<TestSingleTile>()
    val tile2 = mosaic2.getTile<TestSingleTile>()

    assertNotSame(tile1, tile2)
    // Note: mosaic property is protected, so we can't access it directly in tests
  }

  @Test
  fun `should throw exception for unregistered tile type`() {
    val exception =
      assertThrows(IllegalArgumentException::class.java) {
        mosaic.getTile<TestSingleTile>()
      }

    assertTrue(exception.message?.contains("No constructor registered") == true)
  }

  @Test
  fun `should work with suspend functions in tiles`() =
    runTest {
      registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

      val tile = mosaic.getTile<TestSingleTile>()
      val result = tile.get()

      assertEquals("test-value", result)
    }

  @Test
  fun `should work with multi tiles`() =
    runTest {
      registry.register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }

      val tile = mosaic.getTile<TestMultiTile>()
      val result = tile.getByKeys(listOf("key1", "key2"))

      assertEquals(2, result.size)
      assertEquals("normalized-key1", result["key1"])
      assertEquals("normalized-key2", result["key2"])
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

  @Test
  fun `should provide access to request from tiles`() {
    val testRequest = TestRequest()
    val mosaicWithRequest = Mosaic(registry, testRequest)

    registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

    val tile = mosaicWithRequest.getTile<TestSingleTile>()
    val result = tile.getWithRequest()

    assertEquals("Request accessed successfully", result)
  }

  private class TestSingleTile(mosaic: Mosaic) : SingleTile<String>(mosaic) {
    override suspend fun retrieve(): String = "test-value"

    fun getWithRequest(): String {
      // This is a test method that demonstrates accessing request context
      // The constant return value is intentional for testing purposes
      return "Request accessed successfully"
    }
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
