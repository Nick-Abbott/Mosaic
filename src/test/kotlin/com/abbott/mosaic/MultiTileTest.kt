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

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@Suppress("FunctionOnlyReturningConstant", "FunctionMaxLength")
class MultiTileTest {
  private lateinit var testTile: TestMultiTile

  @BeforeEach
  fun setUp() {
    testTile = TestMultiTile()
  }

  @Test
  fun `should retrieve values for missing keys`() =
    runTest {
      val result = testTile.getByKeys(listOf("key1", "key2", "key3"))

      assertEquals(3, result.size)
      assertEquals("value1", result["key1"])
      assertEquals("value2", result["key2"])
      assertEquals("value3", result["key3"])
      assertEquals(1, testTile.retrieveCallCount.get())
    }

  @Test
  fun `should cache values and not call retrieve again`() =
    runTest {
      val result1 = testTile.getByKeys(listOf("key1", "key2"))
      val result2 = testTile.getByKeys(listOf("key1", "key2", "key3"))

      assertEquals(2, result1.size)
      assertEquals(3, result2.size)
      assertEquals("value1", result1["key1"])
      assertEquals("value2", result1["key2"])
      assertEquals("value3", result2["key3"])
      assertEquals(2, testTile.retrieveCallCount.get()) // Only 2 calls for 2 different batches
    }

  @Test
  fun `should handle vararg keys`() =
    runTest {
      val result = testTile.getByKeys("key1", "key2", "key3")

      assertEquals(3, result.size)
      assertEquals("value1", result["key1"])
      assertEquals("value2", result["key2"])
      assertEquals("value3", result["key3"])
      assertEquals(1, testTile.retrieveCallCount.get())
    }

  @Test
  fun `should handle concurrent calls efficiently`() =
    runTest {
      val result1 = testTile.getByKeys(listOf("key1", "key2"))
      val result2 = testTile.getByKeys(listOf("key2", "key3"))
      val result3 = testTile.getByKeys(listOf("key1", "key3"))

      assertEquals(2, result1.size)
      assertEquals(2, result2.size)
      assertEquals(2, result3.size)

      // Should only make 2 retrieve calls (one for each unique batch)
      assertTrue(testTile.retrieveCallCount.get() <= 2)
    }

  @Test
  fun `should handle overlapping keys in concurrent calls`() =
    runTest {
      val result1 = testTile.getByKeys(listOf("key1", "key2"))
      val result2 = testTile.getByKeys(listOf("key1", "key2")) // Same keys

      assertEquals(result1, result2)
      assertEquals(1, testTile.retrieveCallCount.get()) // Only one call for same keys
    }

  @Test
  fun `should handle retrieve errors gracefully`() =
    runTest {
      testTile.shouldThrowError = true

      val exception =
        assertThrows(RuntimeException::class.java) {
          runBlocking { testTile.getByKeys(listOf("key1", "key2")) }
        }

      assertEquals("Retrieve failed", exception.message)
      assertEquals(1, testTile.retrieveCallCount.get())

      // Should allow retry after error
      testTile.shouldThrowError = false
      val result = testTile.getByKeys(listOf("key1", "key2"))
      assertEquals(2, result.size)
      assertEquals(2, testTile.retrieveCallCount.get())
    }

  @Test
  fun `should handle empty key list`() =
    runTest {
      val result = testTile.getByKeys(emptyList())

      assertTrue(result.isEmpty())
      assertEquals(0, testTile.retrieveCallCount.get())
    }

  @Test
  fun `should handle single key`() =
    runTest {
      val result = testTile.getByKeys(listOf("key1"))

      assertEquals(1, result.size)
      assertEquals("value1", result["key1"])
      assertEquals(1, testTile.retrieveCallCount.get())
    }

  @Test
  fun `should work with large number of keys`() =
    runTest {
      val keys = (1..100).map { "key$it" }
      val result = testTile.getByKeys(keys)

      assertEquals(100, result.size)
      keys.forEach { key ->
        val expectedValue = key.replace("key", "value")
        assertEquals(expectedValue, result[key])
      }
      assertEquals(1, testTile.retrieveCallCount.get())
    }

  @Test
  fun `should handle normalization correctly`() =
    runTest {
      val result = testTile.getByKeys(listOf("key1", "key2"))

      assertEquals(2, result.size)
      assertEquals("normalized-value1", result["key1"])
      assertEquals("normalized-value2", result["key2"])
      assertEquals(2, testTile.normalizeCallCount.get()) // One call per key
    }

  private class TestMultiTile : MultiTile<String, List<String>>(Mosaic(MosaicRegistry(), TestRequest())) {
    val retrieveCallCount = AtomicInteger(0)
    val normalizeCallCount = AtomicInteger(0)
    var shouldThrowError = false

    override suspend fun retrieveForKeys(keys: List<String>): List<String> {
      retrieveCallCount.incrementAndGet()

      if (shouldThrowError) {
        throw RuntimeException("Retrieve failed")
      }

      return keys.map { key ->
        key.replace("key", "value")
      }
    }

    override fun normalize(
      key: String,
      response: List<String>,
    ): String {
      normalizeCallCount.incrementAndGet()
      val index = key.replace("key", "").toInt() - 1
      return "normalized-${response[index]}"
    }
  }

  private class TestRequest : MosaicRequest {
    // Simple test implementation
  }
}
