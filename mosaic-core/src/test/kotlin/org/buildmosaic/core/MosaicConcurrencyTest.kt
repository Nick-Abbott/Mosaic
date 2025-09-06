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

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Suppress("FunctionOnlyReturningConstant", "FunctionMaxLength")
class MosaicConcurrencyTest {
  private lateinit var registry: MosaicRegistry
  private lateinit var mosaic: Mosaic

  @BeforeTest
  fun setUp() {
    registry = MosaicRegistry()
    mosaic = Mosaic(registry, TestRequest())
  }

  @Test
  fun `should handle concurrent tile creation with synchronized double-check`() =
    runTest {
      val constructorCallCount = AtomicInteger(0)

      registry.register(TestSingleTile::class) { mosaic ->
        constructorCallCount.incrementAndGet()
        TestSingleTile(mosaic)
      }

      // Launch multiple concurrent getTile calls
      val results =
        coroutineScope {
          (1..10).map {
            async { mosaic.getTile<TestSingleTile>() }
          }.awaitAll()
        }

      // All should return the same instance (synchronized worked)
      results.forEach { tile ->
        assertSame(results[0], tile)
      }

      // Constructor should only be called once (no race condition)
      assertEquals(1, constructorCallCount.get())
    }

  @Test
  fun `should handle MultiTile concurrent access with mutex protection`() =
    runTest {
      val testTile = TestMultiTile()

      // Launch concurrent calls that might hit the mutex
      val results =
        coroutineScope {
          listOf(
            async { testTile.getByKeys(listOf("key1", "key2")) },
            async { testTile.getByKeys(listOf("key2", "key3")) },
            async { testTile.getByKeys(listOf("key1", "key3")) },
          ).awaitAll()
        }

      // Verify all results are correct
      assertEquals(2, results[0].size)
      assertEquals(2, results[1].size)
      assertEquals(2, results[2].size)

      // Should have made efficient batch calls
      assertEquals(2, testTile.retrieveCallCount.get())
    }

  @Test
  fun `should verify error message contains plugin suggestion`() {
    val exception =
      assertFailsWith<IllegalArgumentException> {
        registry.getInstance(UnregisteredTile::class, mosaic)
      }

    // Test the new error message format
    assertTrue(
      exception.message?.contains("mosaic-build-plugin") == true,
      "Error message should mention mosaic-build-plugin",
    )
  }

  private class TestSingleTile(mosaic: Mosaic) : SingleTile<String>(mosaic) {
    override suspend fun retrieve(): String {
      return "test-value"
    }
  }

  private class TestMultiTile : MultiTile<String, List<String>>(
    Mosaic(MosaicRegistry(), TestRequest()),
  ) {
    val retrieveCallCount = AtomicInteger(0)

    override suspend fun retrieveForKeys(keys: List<String>): List<String> {
      retrieveCallCount.incrementAndGet()
      delay(5) // Simulate network call
      return keys.map { it.replace("key", "value") }
    }

    override fun normalize(
      key: String,
      response: List<String>,
    ): String = key.replace("key", "value")
  }

  private class UnregisteredTile(mosaic: Mosaic) : SingleTile<String>(mosaic) {
    override suspend fun retrieve(): String = "unregistered"
  }

  private class TestRequest : MosaicRequest
}
