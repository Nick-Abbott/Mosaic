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

package org.buildmosaic.test

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.buildmosaic.core.multiTile
import org.buildmosaic.core.singleTile
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for TestMosaic constructor and basic properties.
 */
@Suppress("LargeClass", "FunctionMaxLength")
class TestMosaicTest {
  // Test tiles for testing
  private val testSingleTile = singleTile { "test-data" }
  private val testIntTile = singleTile { 42 }
  private val testMultiTile =
    multiTile<String, String> { keys ->
      keys.associateWith { "data-for-$it" }
    }
  private val testIntMultiTile =
    multiTile<Int, String> { keys ->
      keys.associateWith { "value-$it" }
    }
  private val testErrorTile =
    singleTile<String> {
      throw TestException("Test error")
    }
  private val testErrorMultiTile =
    multiTile<String, String> { _ ->
      throw TestException("Multi tile error")
    }

  @Test
  fun `should get single tile values`() =
    runTest {
      val testData = "mocked-data"
      val intData = 123

      mosaicBuilder()
        .withMockTile(testSingleTile, testData)
        .withMockTile(testIntTile, intData)
        .withMosaic {
          assertEquals(testData, compose(testSingleTile))
          assertEquals(intData, compose(testIntTile))
        }
    }

  @Test
  fun `should get multi tile values with collection`() =
    runTest {
      val keys = listOf("key1", "key2")
      val expected = mapOf("key1" to "value1", "key2" to "value2")

      mosaicBuilder()
        .withMockTile(testMultiTile, expected)
        .withMosaic {
          assertEquals(expected, compose(testMultiTile, keys))
        }
    }

  @Test
  fun `should get multi tile values with one key`() =
    runTest {
      val expected = mapOf("a" to "A", "b" to "B", "c" to "C")

      mosaicBuilder()
        .withMockTile(testMultiTile, expected)
        .withMosaic {
          assertEquals(expected["a"]!!, compose(testMultiTile, "a"))
        }
    }

  @Test
  fun `should assert equals for single tile`() =
    runTest {
      val testData = "expected-data"

      mosaicBuilder()
        .withMockTile(testSingleTile, testData)
        .withMosaic {
          assertEquals(testSingleTile, testData)
        }
    }

  @Test
  fun `should assert equals for single tile with custom message`() =
    runTest {
      val testData = "expected-data"
      val customMessage = "Custom assertion message"

      mosaicBuilder()
        .withMockTile(testSingleTile, testData)
        .withMosaic {
          assertEquals(testSingleTile, testData, customMessage)
        }
    }

  @Test
  fun `should fail assert equals for single tile with wrong data`() =
    runTest {
      val testData = "expected-data"
      val wrongData = "wrong-data"

      mosaicBuilder()
        .withMockTile(testSingleTile, testData)
        .withMosaic {
          assertFailsWith<AssertionError> {
            assertEquals(testSingleTile, wrongData)
          }
        }
    }

  @Test
  fun `should fail assert equals for single tile with custom message`() =
    runTest {
      val testData = "expected-data"
      val wrongData = "wrong-data"
      val customMessage = "Custom failure message"

      mosaicBuilder()
        .withMockTile(testSingleTile, testData)
        .withMosaic {
          try {
            assertEquals(testSingleTile, wrongData, customMessage)
            fail("Should have failed")
          } catch (e: AssertionError) {
            assertNotNull(e.message)
            assertTrue(e.message!!.contains(customMessage))
          }
        }
    }

  @Test
  fun `should assert equals for multi tile with collection`() =
    runTest {
      val keys = listOf("key1", "key2")
      val expected = mapOf("key1" to "data-for-key1", "key2" to "data-for-key2")

      mosaicBuilder()
        .withMockTile(testMultiTile, expected)
        .withMosaic {
          assertEquals(testMultiTile, keys, expected)
        }
    }

  @Test
  fun `should assert equals for multi tile with list and custom message`() =
    runTest {
      val keys = listOf("key1", "key2")
      val expected = mapOf("key1" to "data-for-key1", "key2" to "data-for-key2")
      val customMessage = "Multi tile assertion message"

      mosaicBuilder()
        .withMockTile(testMultiTile, expected)
        .withMosaic {
          assertEquals(testMultiTile, keys, expected, customMessage)
        }
    }

  @Test
  fun `should fail assert equals for multi tile with wrong data`() =
    runTest {
      val keys = listOf("key1", "key2")
      val expected = mapOf("key1" to "data-for-key1", "key2" to "data-for-key2")
      val wrongData = mapOf("key1" to "wrong-data", "key2" to "wrong-data")

      mosaicBuilder()
        .withMockTile(testMultiTile, expected)
        .withMosaic {
          assertFailsWith<AssertionError> {
            assertEquals(testMultiTile, keys, wrongData)
          }
        }
    }

  @Test
  fun `should fail assert equals for multi tile with custom message`() =
    runTest {
      val keys = listOf("key1", "key2")
      val expected = mapOf("key1" to "data-for-key1", "key2" to "data-for-key2")
      val wrongData = mapOf("key1" to "wrong-data", "key2" to "wrong-data")
      val customMessage = "Multi tile failure message"

      mosaicBuilder()
        .withMockTile(testMultiTile, expected)
        .withMosaic {
          try {
            assertEquals(testMultiTile, keys, wrongData, customMessage)
            fail("Should have failed")
          } catch (e: AssertionError) {
            assertNotNull(e.message)
            assertTrue(e.message!!.contains(customMessage))
          }
        }
    }

  @Test
  fun `should assert throws for single tile`() =
    runTest {
      mosaicBuilder()
        .withFailedTile(testErrorTile, TestException("Test error"))
        .withMosaic {
          assertThrows(testErrorTile, TestException::class)
        }
    }

  @Test
  fun `should assert throws for single tile with custom message`() =
    runTest {
      val customMessage = "Expected exception message"

      mosaicBuilder()
        .withFailedTile(testErrorTile, TestException("Test error"))
        .withMosaic {
          assertThrows(testErrorTile, TestException::class, customMessage)
        }
    }

  @Test
  fun `should fail assert throws for single tile with wrong exception`() =
    runTest {
      mosaicBuilder()
        .withMockTile(testSingleTile, "normal-data")
        .withMosaic {
          assertFailsWith<AssertionError> {
            assertThrows(testSingleTile, RuntimeException::class)
          }
        }
    }

  @Test
  fun `should fail assert throws for single tile with wrong exception and custom message`() =
    runTest {
      val customMessage = "Wrong exception message"

      mosaicBuilder()
        .withMockTile(testSingleTile, "normal-data")
        .withMosaic {
          try {
            assertThrows(testSingleTile, RuntimeException::class, customMessage)
            fail("Should have failed")
          } catch (e: AssertionError) {
            assertNotNull(e.message)
            assertTrue(e.message!!.contains(customMessage))
          }
        }
    }

  @Test
  fun `should assert throws for multi tile`() =
    runTest {
      val keys = listOf("key1")

      mosaicBuilder()
        .withFailedTile(testErrorMultiTile, TestException("Multi error"))
        .withMosaic {
          assertThrows(testErrorMultiTile, keys, TestException::class)
        }
    }

  @Test
  fun `should fail assert throws for multi tile with wrong exception`() =
    runTest {
      val keys = listOf("key1")
      val expected = mapOf("key1" to "normal-data")

      mosaicBuilder()
        .withMockTile(testMultiTile, expected)
        .withMosaic {
          assertFailsWith<AssertionError> {
            assertThrows(testMultiTile, keys, RuntimeException::class)
          }
        }
    }

  @Test
  fun `should handle null values in assertions`() =
    runTest {
      val nullableTile = singleTile<String?> { null }

      mosaicBuilder()
        .withMockTile(nullableTile, null)
        .withMosaic {
          assertEquals(nullableTile, null)
        }
    }

  @Test
  fun `should handle empty collections in multi tile assertions`() =
    runTest {
      val emptyKeys = emptyList<String>()
      val emptyResult = emptyMap<String, String>()

      mosaicBuilder()
        .withMockTile(testMultiTile, emptyResult)
        .withMosaic {
          assertEquals(testMultiTile, emptyKeys, emptyResult)
        }
    }

  @Test
  fun `should handle complex data types in assertions`() =
    runTest {
      data class ComplexData(val id: Int, val name: String, val nested: Map<String, List<Int>>)
      val complexTile =
        singleTile {
          ComplexData(1, "test", mapOf("list" to listOf(1, 2, 3)))
        }
      val complexData = ComplexData(99, "complex", mapOf("items" to listOf(4, 5, 6)))

      mosaicBuilder()
        .withMockTile(complexTile, complexData)
        .withMosaic {
          assertEquals(complexTile, complexData)
        }
    }

  @Test
  fun `should support different key types in multi tiles`() =
    runTest {
      val intKeys = listOf(1, 2, 3)
      val intExpected = mapOf(1 to "one", 2 to "two", 3 to "three")

      mosaicBuilder()
        .withMockTile(testIntMultiTile, intExpected)
        .withMosaic {
          assertEquals(testIntMultiTile, intKeys, intExpected)
        }
    }

  @Test
  fun `should handle mixed success and failure scenarios`() =
    runTest {
      val successData = "success"

      class MyFakeException : Exception("Expected failure")

      mosaicBuilder()
        .withMockTile(testSingleTile, successData)
        .withFailedTile(testErrorTile, MyFakeException())
        .withMosaic {
          // Success case
          assertEquals(testSingleTile, successData)

          // Failure case
          assertThrows(testErrorTile, MyFakeException::class)
        }
    }

  @Test
  fun `should compose single tile asynchronously`() =
    runTest {
      val testData = "async-data"

      mosaicBuilder()
        .withMockTile(testSingleTile, testData)
        .withMosaic {
          val deferred = composeAsync(testSingleTile)
          val result = deferred.await()
          assertEquals(testData, result)
        }
    }

  @Test
  fun `should compose multi tile asynchronously with collection`() =
    runTest {
      val keys = listOf("key1", "key2")
      val expected = mapOf("key1" to "value1", "key2" to "value2")

      mosaicBuilder()
        .withMockTile(testMultiTile, expected)
        .withMosaic {
          val deferredMap = composeAsync(testMultiTile, keys)
          val result = deferredMap.mapValues { it.value.await() }
          assertEquals(expected, result)
        }
    }

  @Test
  fun `should compose multi tile asynchronously with single key`() =
    runTest {
      val expected = mapOf("test-key" to "test-value")

      mosaicBuilder()
        .withMockTile(testMultiTile, expected)
        .withMosaic {
          val deferred = composeAsync(testMultiTile, "test-key")
          val result = deferred.await()
          assertEquals("test-value", result)
        }
    }

  @Test
  fun `should allow non-mocked tiles to work correctly within composed tiles`() =
    runTest {
      val realTile = singleTile { "real-data" }
      val mockedTile = singleTile { "mocked-data" }

      val composedTile =
        singleTile {
          val realResult = compose(realTile)
          val mockedResult = compose(mockedTile)
          "$realResult + $mockedResult"
        }

      mosaicBuilder()
        .withMockTile(mockedTile, "test-mocked-data")
        // realTile is not mocked, should use original implementation
        .withMosaic {
          val result = compose(composedTile)
          assertEquals("real-data + test-mocked-data", result)
        }
    }

  @Test
  fun `should allow non-mocked multi tiles to work correctly within composed tiles`() =
    runTest {
      val realMultiTile =
        multiTile<String, String> { keys ->
          keys.associateWith { "real-$it" }
        }
      val mockedMultiTile =
        multiTile<String, String> { keys ->
          keys.associateWith { "mocked-$it" }
        }

      val composedTile =
        singleTile {
          val realResults = compose(realMultiTile, listOf("a", "b"))
          val mockedResults = compose(mockedMultiTile, listOf("x", "y"))
          "Real: ${realResults.values.joinToString()}, Mocked: ${mockedResults.values.joinToString()}"
        }

      mosaicBuilder()
        .withMockTile(mockedMultiTile, mapOf("x" to "test-x", "y" to "test-y"))
        // realMultiTile is not mocked, should use original implementation
        .withMosaic {
          val result = compose(composedTile)
          assertEquals("Real: real-a, real-b, Mocked: test-x, test-y", result)
        }
    }

  @Test
  fun `should handle mixed mocked and non-mocked tiles in complex composition`() =
    runTest {
      val baseTile = singleTile { 10 }
      val multiplierTile = singleTile { 3 }
      val formatTile = singleTile<String> { "formatted" }

      val complexTile =
        singleTile {
          val base = compose(baseTile)
          val multiplier = compose(multiplierTile)
          val format = compose(formatTile)
          "$format: ${base * multiplier}"
        }

      mosaicBuilder()
        .withMockTile(multiplierTile, 5) // Mock multiplier
        .withMockTile(formatTile, "result") // Mock format
        // baseTile is not mocked, should return 10
        .withMosaic {
          val result = compose(complexTile)
          assertEquals("result: 50", result)
        }
    }

  @Test
  fun `should provide access to canvas from test mosaic`() =
    runTest {
      data class TestService(val name: String)
      val testService = TestService("test-service")

      mosaicBuilder()
        .withCanvasSource(testService)
        .withMosaic {
          assertEquals(testService, canvas.source(TestService::class))
        }
    }

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  @Test
  fun `execution inherits caller context and virtual time`() =
    runTest {
      val ambient = ThreadLocal<String>()
      withContext(CoroutineName("test request") + ambient.asContextElement("tenant")) {
        val caller = currentCoroutineContext()
        val start = testScheduler.currentTime
        mosaicBuilder().withMosaic {
          assertSame(caller[Job], currentCoroutineContext()[Job])
          compose(
            singleTile {
              val producer = currentCoroutineContext()
              assertSame(caller[ContinuationInterceptor], producer[ContinuationInterceptor])
              assertEquals("test request", producer[CoroutineName]?.name)
              assertEquals("tenant", ambient.get())

              fun descendants(job: Job): Sequence<Job> = job.children.flatMap { sequenceOf(it) + descendants(it) }
              assertTrue(descendants(caller[Job]!!).any { it === producer[Job] })
              delay(200)
            },
          )
        }
        assertEquals(200L, testScheduler.currentTime - start)
        assertTrue(caller[Job]!!.children.none())
      }
    }

  @Test fun `normal and exceptional exit cancel speculation and join attached cleanup`() =
    runTest {
      for (exceptional in listOf(false, true)) {
        val cleanup = CompletableDeferred<Unit>()
        var cleaned = false
        lateinit var speculative: kotlinx.coroutines.Deferred<Nothing>
        val failure = IllegalStateException("block failure")
        val owner =
          async {
            val execute: suspend () -> Unit = {
              mosaicBuilder().withMosaic {
                assertEquals(
                  singleTile {
                    CoroutineScope(currentCoroutineContext()).launch {
                      try {
                        awaitCancellation()
                      } finally {
                        withContext(NonCancellable) {
                          cleanup.await()
                          cleaned = true
                        }
                      }
                    }
                    42
                  },
                  42,
                )
                speculative = composeAsync(singleTile { awaitCancellation() })
                testScheduler.runCurrent()
                if (exceptional) throw failure
              }
            }
            if (exceptional) {
              assertSame(failure, assertFailsWith<IllegalStateException> { execute() })
            } else {
              execute()
            }
          }
        testScheduler.runCurrent()
        assertFalse(owner.isCompleted)
        assertFalse(cleaned)
        cleanup.complete(Unit)
        owner.await()
        assertTrue(cleaned)
        assertTrue(speculative.isCancelled)
        assertTrue(owner.children.none())
      }
    }

  @Test fun `enclosing coroutine cancellation cancels producers before return`() =
    runTest {
      var cleaned = false
      lateinit var result: kotlinx.coroutines.Deferred<Nothing>
      val owner =
        launch {
          mosaicBuilder().withMosaic {
            result =
              composeAsync(
                singleTile {
                  try {
                    awaitCancellation()
                  } finally {
                    withContext(NonCancellable) {
                      delay(50)
                      cleaned = true
                    }
                  }
                },
              )
            result.await()
          }
        }
      testScheduler.runCurrent()
      owner.cancelAndJoin()
      assertTrue(cleaned)
      assertTrue(result.isCancelled)
      assertTrue(owner.children.none())
    }

  @Test fun `one waiter cancellation preserves shared work and each execution gets a fresh cache`() =
    runTest {
      var calls = 0
      val gate = CompletableDeferred<Unit>()
      val dependency = singleTile<Int> { error("use substitute") }
      val subject = singleTile { compose(dependency) }
      val builder =
        mosaicBuilder().withCustomTile(dependency) {
          calls++
          gate.await()
          7
        }
      builder.withMosaic {
        val waiter = launch { compose(subject) }
        testScheduler.runCurrent()
        waiter.cancelAndJoin()
        assertFalse(composeAsync(subject).isCancelled)
        gate.complete(Unit)
        assertEquals(subject, 7)
        assertEquals(subject, 7)
        assertEquals(1, calls)
      }
      builder.withMosaic { assertEquals(subject, 7) }
      assertEquals(2, calls)
    }
}

// Test exception class for testing error scenarios
class TestException(message: String) : Exception(message)
