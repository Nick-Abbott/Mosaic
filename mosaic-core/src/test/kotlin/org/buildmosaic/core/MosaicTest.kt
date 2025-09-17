package org.buildmosaic.core

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.injection.canvas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

@Suppress("LargeClass", "FunctionMaxLength")
class MosaicTest {
  @Test
  fun `should retrieve a singleTile asynchronously`() =
    runTest {
      val testDispatcher = StandardTestDispatcher(testScheduler)
      val mosaic = MosaicImpl(canvas { }, testDispatcher)

      val resultValue = "test-result"
      val delayLength = 100L
      val testTile =
        singleTile {
          delay(delayLength)
          resultValue
        }

      var result: Deferred<String>? = null
      launch {
        val workDuration =
          testScheduler.timeSource.measureTime {
            result = mosaic.composeAsync(testTile)
          }
        assertEquals(0.milliseconds, workDuration)
      }

      testScheduler.runCurrent()
      assertNotNull(result)
      assertFalse(result.isCompleted)
      testScheduler.advanceTimeBy(delayLength.milliseconds)
      testScheduler.runCurrent()
      assertTrue(result.isCompleted)
      assertEquals(resultValue, result.await())
    }

  @Test
  fun `should retrieve a singleTile synchronously`() =
    runTest {
      val testDispatcher = StandardTestDispatcher(testScheduler)
      val mosaic = MosaicImpl(canvas { }, testDispatcher)

      val resultValue = "test-result"
      val delayLength = 100L
      val testTile =
        singleTile {
          delay(delayLength)
          resultValue
        }

      var result: String? = null
      launch {
        val workDuration =
          testScheduler.timeSource.measureTime {
            result = mosaic.compose(testTile)
          }
        assertEquals(delayLength.milliseconds, workDuration)
      }

      testScheduler.runCurrent()
      assertNull(result)
      testScheduler.advanceTimeBy(delayLength.milliseconds)
      testScheduler.runCurrent()
      assertEquals(resultValue, result)
    }

  @Test
  fun `should retrieve a mutltiTile asynchronously`() =
    runTest {
      val testDispatcher = StandardTestDispatcher(testScheduler)
      val mosaic = MosaicImpl(canvas { }, testDispatcher)

      val resultMap = mapOf("a" to "foo", "b" to "bar", "c" to "baz")
      val delayLength = 100L
      val testTile =
        multiTile {
          delay(delayLength)
          resultMap
        }

      var result: Map<String, Deferred<String>>? = null
      launch {
        val workDuration =
          testScheduler.timeSource.measureTime {
            result = mosaic.composeAsync(testTile, resultMap.keys)
          }
        assertEquals(0.milliseconds, workDuration)
      }

      testScheduler.runCurrent()
      assertNotNull(result)
      assertTrue(result.filterValues { it.isCompleted }.isEmpty())
      testScheduler.advanceTimeBy(delayLength.milliseconds)
      testScheduler.runCurrent()
      assertTrue(result.filterValues { !it.isCompleted }.isEmpty())
      assertEquals(resultMap, result.mapValues { it.value.await() })
    }

  @Test
  fun `should retrieve a multiTile synchronously`() =
    runTest {
      val testDispatcher = StandardTestDispatcher(testScheduler)
      val mosaic = MosaicImpl(canvas { }, testDispatcher)

      val resultMap = mapOf("a" to "foo", "b" to "bar", "c" to "baz")
      val delayLength = 100L
      val testTile =
        multiTile {
          delay(delayLength)
          resultMap
        }

      var result: Map<String, String>? = null
      launch {
        val workDuration =
          testScheduler.timeSource.measureTime {
            result = mosaic.compose(testTile, resultMap.keys)
          }
        assertEquals(delayLength.milliseconds, workDuration)
      }

      testScheduler.runCurrent()
      assertNull(result)
      testScheduler.advanceTimeBy(delayLength.milliseconds)
      testScheduler.runCurrent()
      assertEquals(resultMap, result)
    }

  @Test
  fun `should receive a single key from a multiTile asynchronously`() =
    runTest {
      val testDispatcher = StandardTestDispatcher(testScheduler)
      val mosaic = MosaicImpl(canvas { }, testDispatcher)

      val resultMap = mapOf("a" to "foo", "b" to "bar", "c" to "baz")
      val delayLength = 100L
      val testTile =
        multiTile {
          delay(delayLength)
          resultMap
        }

      var result: Deferred<String>? = null
      launch {
        val workDuration =
          testScheduler.timeSource.measureTime {
            result = mosaic.composeAsync(testTile, "a")
          }
        assertEquals(0.milliseconds, workDuration)
      }

      testScheduler.runCurrent()
      assertNotNull(result)
      assertFalse(result.isCompleted)
      testScheduler.advanceTimeBy(delayLength.milliseconds)
      testScheduler.runCurrent()
      assertTrue(result.isCompleted)
      assertEquals(resultMap["a"], result.await())
    }

  @Test
  fun `should receive a single key from a multiTile synchronously`() =
    runTest {
      val testDispatcher = StandardTestDispatcher(testScheduler)
      val mosaic = MosaicImpl(canvas { }, testDispatcher)

      val resultMap = mapOf("a" to "foo", "b" to "bar", "c" to "baz")
      val delayLength = 100L
      val testTile =
        multiTile {
          delay(delayLength)
          resultMap
        }

      var result: String? = null
      launch {
        val workDuration =
          testScheduler.timeSource.measureTime {
            result = mosaic.compose(testTile, "a")
          }
        assertEquals(delayLength.milliseconds, workDuration)
      }

      testScheduler.runCurrent()
      assertNull(result)
      testScheduler.advanceTimeBy(delayLength.milliseconds)
      testScheduler.runCurrent()
      assertEquals(resultMap["a"], result)
    }

  @Test
  fun `should bubble up the error if a single tile fails`() =
    runTest {
      class MyFakeException : Exception("test")

      val testDispatcher = StandardTestDispatcher(testScheduler)
      val mosaic = MosaicImpl(canvas { }, testDispatcher)
      assertFailsWith<MyFakeException> {
        mosaic.compose(singleTile<String> { throw MyFakeException() })
      }
    }

  @Test
  fun `should bubble up the error if a multi tile fails`() =
    runTest {
      class MyFakeException : Exception("test")

      val testDispatcher = StandardTestDispatcher(testScheduler)
      val mosaic = MosaicImpl(canvas { }, testDispatcher)
      assertFailsWith<MyFakeException> {
        mosaic.compose(multiTile<String, String> { throw MyFakeException() }, listOf("abc"))
      }
    }

  @Test
  fun `should fail if a key is missing from the response`() =
    runTest {
      val testDispatcher = StandardTestDispatcher(testScheduler)
      val mosaic = MosaicImpl(canvas { }, testDispatcher)
      val tile = multiTile { mapOf("a" to "a") }

      assertFailsWith<NoSuchElementException> { mosaic.compose(tile, listOf("a", "b")) }
    }

  @Test
  fun `should return empty response if no keys are provided`() =
    runTest {
      val testDispatcher = StandardTestDispatcher(testScheduler)
      val mosaic = MosaicImpl(canvas { }, testDispatcher)
      val tile = multiTile { mapOf("a" to "a", "b" to "b", "c" to "c") }
      assertEquals(emptyMap(), mosaic.compose(tile, emptyList()))
    }
}
