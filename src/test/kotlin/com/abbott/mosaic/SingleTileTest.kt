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

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@Suppress("FunctionOnlyReturningConstant", "FunctionMaxLength")
class SingleTileTest {
  private lateinit var testTile: TestSingleTile

  @BeforeEach
  fun setUp() {
    testTile = TestSingleTile()
  }

  @Test
  fun `should retrieve value on first call`() =
    runTest {
      val result = testTile.get()

      assertEquals("test-value", result)
      assertEquals(1, testTile.retrieveCallCount.get())
    }

  @Test
  fun `should cache value and not call retrieve again`() =
    runTest {
      val result1 = testTile.get()
      val result2 = testTile.get()
      val result3 = testTile.get()

      assertEquals("test-value", result1)
      assertEquals("test-value", result2)
      assertEquals("test-value", result3)
      assertEquals(1, testTile.retrieveCallCount.get())
    }

  @Test
  fun `should handle concurrent calls efficiently`() =
    runTest {
      val result1 = testTile.get()
      val result2 = testTile.get()
      val result3 = testTile.get()

      assertEquals("test-value", result1)
      assertEquals("test-value", result2)
      assertEquals("test-value", result3)
      assertEquals(1, testTile.retrieveCallCount.get())
    }

  @Test
  fun `should handle retrieve errors gracefully`() =
    runTest {
      testTile.shouldThrowError = true

      val exception =
        assertThrows(RuntimeException::class.java) {
          runBlocking { testTile.get() }
        }

      assertEquals("Retrieve failed", exception.message)
      assertEquals(1, testTile.retrieveCallCount.get())

      // Should allow retry after error
      testTile.shouldThrowError = false
      val result = testTile.get()
      assertEquals("test-value", result)
      assertEquals(2, testTile.retrieveCallCount.get())
    }

  @Test
  fun `should handle different values for different instances`() =
    runTest {
      val tile1 = TestSingleTile("value1")
      val tile2 = TestSingleTile("value2")

      val result1 = tile1.get()
      val result2 = tile2.get()

      assertEquals("value1", result1)
      assertEquals("value2", result2)
    }

  @Test
  fun `should work with suspend functions that take time`() =
    runTest {
      testTile.retrieveDelay = 100L

      val startTime = System.currentTimeMillis()
      val result = testTile.get()
      val endTime = System.currentTimeMillis()

      assertEquals("test-value", result)
      assertTrue(endTime - startTime >= 100)
      assertEquals(1, testTile.retrieveCallCount.get())
    }

  private class TestSingleTile(
    private val value: String = "test-value",
  ) : SingleTile<String>(Mosaic(MosaicRegistry(), TestRequest())) {
    val retrieveCallCount = AtomicInteger(0)
    var shouldThrowError = false
    var retrieveDelay = 0L

    override suspend fun retrieve(): String {
      retrieveCallCount.incrementAndGet()

      if (shouldThrowError) {
        throw RuntimeException("Retrieve failed")
      }

      if (retrieveDelay > 0) {
        delay(retrieveDelay)
      }

      return value
    }
  }

  private class TestRequest : MosaicRequest {
    // Simple test implementation
  }
}
