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
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.injection.CanvasKey
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.mapValues
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@Suppress("FunctionOnlyReturningConstant", "FunctionMaxLength", "LargeClass")
class MosaicConcurrencyTest {
  private lateinit var mosaic: Mosaic

  @BeforeTest
  fun setUp() {
    val emptyCanvas =
      object : Canvas {
        override fun <T : Any> sourceOr(key: CanvasKey<T>): T? = null
      }
    mosaic = MosaicImpl(emptyCanvas)
  }

  @Test
  fun `should handle concurrent single tile composition`() =
    runTest {
      val constructorCallCount = AtomicInteger(0)

      val testTile =
        singleTile {
          constructorCallCount.incrementAndGet()
          delay(10) // Simulate some work
          "test-value"
        }

      // Launch multiple concurrent compose calls
      val results =
        coroutineScope {
          (1..10).map {
            async { mosaic.compose(testTile) }
          }.awaitAll()
        }

      // All should return the same value
      results.forEach { result ->
        assertEquals("test-value", result)
      }

      // The tile block should only be executed once (synchronized worked)
      assertEquals(1, constructorCallCount.get())
    }

  @Test
  fun `should handle concurrent single tile async composition`() =
    runTest {
      val constructorCallCount = AtomicInteger(0)

      val testTile =
        singleTile {
          constructorCallCount.incrementAndGet()
          delay(10) // Simulate some work
          "test-value"
        }

      // Launch multiple concurrent composeAsync calls
      val deferredResults =
        coroutineScope {
          (1..10).map {
            async { mosaic.composeAsync(testTile) }
          }.awaitAll()
        }

      // All deferred results should be the same instance (cached)
      val firstDeferred = deferredResults[0]
      deferredResults.forEach { deferred ->
        assertSame(firstDeferred, deferred)
      }

      // All should return the same value
      val results = deferredResults.awaitAll()
      results.forEach { result ->
        assertEquals("test-value", result)
      }

      // The tile block should only be executed once (synchronized worked)
      assertEquals(1, constructorCallCount.get())
    }

  @Test
  fun `should handle concurrent MultiTile access`() =
    runTest {
      val retrieveCallCount = AtomicInteger(0)

      val testTile =
        multiTile { keys: Set<String> ->
          retrieveCallCount.incrementAndGet()
          delay(5) // Simulate network call
          keys.associateWith { it.replace("key", "value") }
        }

      // Launch concurrent calls that might hit the mutex
      val results =
        coroutineScope {
          listOf(
            async { mosaic.compose(testTile, listOf("key1", "key2")) },
            async { mosaic.compose(testTile, listOf("key2", "key3")) },
            async { mosaic.compose(testTile, listOf("key1", "key3")) },
          ).awaitAll()
        }

      // Verify all results are correct
      assertEquals(mapOf("key1" to "value1", "key2" to "value2"), results[0])
      assertEquals(mapOf("key2" to "value2", "key3" to "value3"), results[1])
      assertEquals(mapOf("key1" to "value1", "key3" to "value3"), results[2])

      assertEquals(2, retrieveCallCount.get())
    }

  @Test
  fun `should handle concurrent MultiTile access with async composition`() =
    runTest {
      val retrieveCallCount = AtomicInteger(0)

      val testTile =
        multiTile { keys: Set<String> ->
          retrieveCallCount.incrementAndGet()
          delay(5) // Simulate network call
          keys.associateWith { it.replace("key", "value") }
        }

      // Launch concurrent async calls that might hit the mutex
      val deferredResults =
        coroutineScope {
          listOf(
            async { mosaic.composeAsync(testTile, listOf("key1", "key2")) },
            async { mosaic.composeAsync(testTile, listOf("key2", "key3")) },
            async { mosaic.composeAsync(testTile, listOf("key1", "key3")) },
          ).awaitAll()
        }

      // Await all results
      val results =
        deferredResults.map { deferredMap ->
          deferredMap.mapValues { it.value.await() }
        }

      // Verify all results are correct
      assertEquals(mapOf("key1" to "value1", "key2" to "value2"), results[0])
      assertEquals(mapOf("key2" to "value2", "key3" to "value3"), results[1])
      assertEquals(mapOf("key1" to "value1", "key3" to "value3"), results[2])

      assertEquals(2, retrieveCallCount.get())
    }

  @Test
  fun `should handle single key MultiTile concurrent access`() =
    runTest {
      val retrieveCallCount = AtomicInteger(0)

      val testTile =
        multiTile { keys: Set<String> ->
          retrieveCallCount.incrementAndGet()
          delay(5) // Simulate network call
          keys.associateWith { it.replace("key", "value") }
        }

      // Launch concurrent calls for single keys
      val results =
        coroutineScope {
          listOf(
            async { mosaic.compose(testTile, "key1") },
            async { mosaic.compose(testTile, "key2") },
            async { mosaic.compose(testTile, "key1") },
          ).awaitAll()
        }

      // Verify all results are correct
      assertEquals("value1", results[0])
      assertEquals("value2", results[1])
      assertEquals("value1", results[2]) // Same as first

      assertEquals(2, retrieveCallCount.get())
    }
}
