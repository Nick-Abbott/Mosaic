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

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.injection.CanvasKey
import org.buildmosaic.core.multiTile
import org.buildmosaic.core.singleTile
import org.buildmosaic.core.source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

@Suppress("LargeClass", "FunctionMaxLength")
class TestMosaicBuilderTest {
  // Test tiles for single tile operations
  private val testSingleTile = singleTile { "original" }
  private val testIntTile = singleTile { 42 }
  private val testBooleanTile = singleTile { true }

  // Test tiles for multi tile operations
  private val testMultiTile = multiTile<String, String> { keys -> keys.associateWith { "original-$it" } }
  private val testIntMultiTile = multiTile<Int, String> { keys -> keys.associateWith { "value-$it" } }

  @Test
  fun `builds a test mosaic`() =
    runTest {
      mosaicBuilder().withMosaic { assertIs<TestMosaic>(this) }
    }

  @Test
  fun `registers successful mock single tiles`() =
    runTest {
      val singleTileData = "mocked-data"
      val intTileData = 123
      val booleanTileData = false

      mosaicBuilder()
        .withMockTile(testSingleTile, singleTileData)
        .withMockTile(testIntTile, intTileData)
        .withMockTile(testBooleanTile, booleanTileData)
        .withMosaic {
          assertEquals(testSingleTile, singleTileData)
          assertEquals(testIntTile, intTileData)
          assertEquals(testBooleanTile, booleanTileData)
        }
    }

  @Test
  fun `registers successful mock multi tiles`() =
    runTest {
      val multiTileData = mapOf("a" to "A", "b" to "B")
      val intMultiTileData = mapOf(1 to "one", 2 to "two")

      mosaicBuilder()
        .withMockTile(testMultiTile, multiTileData)
        .withMockTile(testIntMultiTile, intMultiTileData)
        .withMosaic {
          assertEquals(testMultiTile, multiTileData.keys, multiTileData)
          assertEquals(testIntMultiTile, intMultiTileData.keys, intMultiTileData)
        }
    }

  @Test
  fun `registers failed single tiles`() =
    runTest {
      val exception = IllegalStateException("test error")
      val runtimeException = RuntimeException("runtime error")

      mosaicBuilder()
        .withFailedTile(testSingleTile, exception)
        .withFailedTile(testIntTile, runtimeException)
        .withMosaic {
          assertThrows(testSingleTile, IllegalStateException::class)
          assertThrows(testIntTile, RuntimeException::class)
        }
    }

  @Test
  fun `registers failed multi tiles`() =
    runTest {
      val exception = IllegalStateException("multi tile error")
      val runtimeException = RuntimeException("multi runtime error")

      mosaicBuilder()
        .withFailedTile(testMultiTile, exception)
        .withFailedTile(testIntMultiTile, runtimeException)
        .withMosaic {
          assertThrows(testMultiTile, listOf("a"), IllegalStateException::class)
          assertThrows(testIntMultiTile, listOf(1), RuntimeException::class)
        }
    }

  @Test
  fun `registers delayed single tiles`() =
    runTest {
      val singleDelay = 50L
      val singleData = "delayed-single"
      val intDelay = 75L
      val intData = 999

      mosaicBuilder()
        .withDelayedTile(testSingleTile, singleData, singleDelay)
        .withDelayedTile(testIntTile, intData, intDelay)
        .withMosaic {
          var singleResult: String? = null
          var intResult: Int? = null

          launch {
            val workDuration =
              testScheduler.timeSource.measureTime {
                singleResult = compose(testSingleTile)
                intResult = compose(testIntTile)
              }
            assertEquals((singleDelay + intDelay).milliseconds, workDuration)
          }

          testScheduler.runCurrent()
          testScheduler.advanceTimeBy(10.milliseconds)
          assertNull(singleResult)
          assertNull(intResult)

          testScheduler.advanceTimeBy(singleDelay.milliseconds)
          assertEquals(singleData, singleResult)
          assertNull(intResult)

          testScheduler.advanceTimeBy(intDelay.milliseconds)
          assertEquals(singleData, singleResult)
          assertEquals(intData, intResult)

          testScheduler.advanceUntilIdle()
        }
    }

  @Test
  fun `registers delayed multi tiles`() =
    runTest {
      val multiDelay = 100L
      val multiData = mapOf("x" to "X", "y" to "Y")
      val intMultiDelay = 150L
      val intMultiData = mapOf(10 to "ten", 20 to "twenty")

      mosaicBuilder()
        .withDelayedTile(testMultiTile, multiData, multiDelay)
        .withDelayedTile(testIntMultiTile, intMultiData, intMultiDelay)
        .withMosaic {
          var multiResult: Map<String, String>? = null
          var intMultiResult: Map<Int, String>? = null

          launch {
            val workDuration =
              testScheduler.timeSource.measureTime {
                multiResult = compose(testMultiTile, multiData.keys)
                intMultiResult = compose(testIntMultiTile, intMultiData.keys)
              }
            assertEquals((multiDelay + intMultiDelay).milliseconds, workDuration)
          }

          testScheduler.runCurrent()
          testScheduler.advanceTimeBy(10.milliseconds)
          assertNull(multiResult)
          assertNull(intMultiResult)

          testScheduler.advanceTimeBy(multiDelay.milliseconds)
          assertEquals(multiData, multiResult)
          assertNull(intMultiResult)

          testScheduler.advanceTimeBy(intMultiDelay.milliseconds)
          assertEquals(multiData, multiResult)
          assertEquals(intMultiData, intMultiResult)

          testScheduler.advanceUntilIdle()
        }
    }

  @Test
  fun `registers custom single tiles`() =
    runTest {
      mosaicBuilder()
        .withCustomTile(testSingleTile) { "custom-single" }
        .withCustomTile(testIntTile) { 777 }
        .withMosaic {
          assertEquals(testSingleTile, "custom-single")
          assertEquals(testIntTile, 777)
        }
    }

  @Test
  fun `registers custom multi tiles`() =
    runTest {
      val inputStringKeys = setOf("a", "b")
      val inputIntKeys = setOf(1, 2)

      mosaicBuilder()
        .withCustomTile(testMultiTile) { keys ->
          keys.associateWith { it.uppercase() + "-custom" }
        }
        .withCustomTile(testIntMultiTile) { keys ->
          keys.associateWith { "custom-${it * 10}" }
        }
        .withMosaic {
          val expectedStringResult = inputStringKeys.associateWith { it.uppercase() + "-custom" }
          val expectedIntResult = inputIntKeys.associateWith { "custom-${it * 10}" }

          assertEquals(testMultiTile, inputStringKeys, expectedStringResult)
          assertEquals(testIntMultiTile, inputIntKeys, expectedIntResult)
        }
    }

  @Test
  fun `supports dependency injection with KClass`() =
    runTest {
      data class TestService(val name: String)
      val testService = TestService("test-service")

      mosaicBuilder()
        .withCanvasSource(TestService::class, testService)
        .withMosaic {
          assertEquals(singleTile { source<TestService>() }, testService)
        }
    }

  @Test
  fun `supports dependency injection with reified type`() =
    runTest {
      data class AnotherService(val value: Int)
      val anotherService = AnotherService(42)

      mosaicBuilder()
        .withCanvasSource(anotherService)
        .withMosaic {
          assertEquals(singleTile { source<AnotherService>() }, anotherService)
        }
    }

  @Test
  fun `supports qualified and keyed source overloads`() =
    runTest {
      val key = CanvasKey(String::class, "keyed")

      mosaicBuilder()
        .withCanvasSource(String::class, "explicit", "first")
        .withCanvasSource("reified", "second")
        .withCanvasSource(key, "third")
        .withMosaic {
          assertEquals(singleTile { source<String>("explicit") }, "first")
          assertEquals(singleTile { source<String>("reified") }, "second")
          assertEquals(singleTile { source(key) }, "third")
        }
    }

  @Test
  fun `builder methods return the same builder for chaining`() =
    runTest {
      val builder = mosaicBuilder()
      val result =
        builder.withMockTile(testSingleTile, "test")
          .withMockTile(testMultiTile, mapOf("a" to "A"))
          .withFailedTile(testIntTile, RuntimeException("error"))
          .withDelayedTile(testBooleanTile, true, 100L)
          .withCanvasSource(String::class, "injected")
      kotlin.test.assertSame(builder, result)
      result.withMosaic { assertIs<TestMosaic>(this) }
    }

  @Test
  fun `handles null values in mock tiles`() =
    runTest {
      val nullableTile = singleTile<String?> { null }

      mosaicBuilder()
        .withMockTile(nullableTile, null)
        .withMosaic {
          assertEquals(nullableTile, null)
        }
    }

  @Test
  fun `handles empty maps in multi tiles`() =
    runTest {
      val emptyMap = emptyMap<String, String>()

      mosaicBuilder()
        .withMockTile(testMultiTile, emptyMap)
        .withMosaic {
          assertEquals(testMultiTile, emptySet(), emptyMap)
        }
    }

  @Test
  fun `supports complex data types`() =
    runTest {
      data class ComplexData(val id: Int, val name: String, val tags: List<String>)
      val complexTile = singleTile { ComplexData(1, "test", listOf("tag1", "tag2")) }
      val complexData = ComplexData(99, "mocked", listOf("mock", "test"))

      mosaicBuilder()
        .withMockTile(complexTile, complexData)
        .withMosaic {
          assertEquals(complexTile, complexData)
        }
    }

  @Test
  fun `supports nested tile composition in custom tiles`() =
    runTest {
      val baseTile = singleTile { "base" }
      val composedTile =
        singleTile {
          val base = compose(baseTile)
          "composed-$base"
        }

      mosaicBuilder()
        .withMockTile(baseTile, "mocked-base")
        .withCustomTile(composedTile) {
          val base = compose(baseTile)
          "custom-composed-$base"
        }
        .withMosaic {
          assertEquals(composedTile, "custom-composed-mocked-base")
        }
    }

  @Test
  fun `handles multiple registrations of same tile type`() =
    runTest {
      // Last registration should win

      mosaicBuilder()
        .withMockTile(testSingleTile, "first")
        .withMockTile(testSingleTile, "second")
        .withMockTile(testSingleTile, "third")
        .withMosaic {
          assertEquals(testSingleTile, "third")
        }
    }

  @Test fun `execution snapshots all configuration and later reuse sees mutations`() =
    runTest {
      val single = singleTile { "real single" }
      val multi = multiTile<Int, String> { keys -> keys.associateWith { "real multi" } }
      val nested = singleTile { "real nested" }
      val customMulti = multiTile<Int, String> { error("substitute") }
      val realMulti = multiTile<Int, String> { keys -> keys.associateWith { compose(nested) } }
      val subject = singleTile { compose(realMulti, 1) + compose(customMulti, 1) + source<String>() }
      val builder =
        mosaicBuilder()
          .withMockTile(single, "old single")
          .withMockTile(multi, mapOf(1 to "old multi"))
          .withCanvasSource("old source")
          .withCustomTile(nested) { compose(single) }
          .withCustomTile(customMulti) { keys -> keys.associateWith { compose(multi, it) } }
      builder.withMosaic {
        builder.withMockTile(single, "new single")
          .withMockTile(multi, mapOf(1 to "new multi"))
          .withCanvasSource("new source")
        // No cache has been populated: nested real and custom providers must use the snapshot.
        assertEquals(subject, "old singleold multiold source")
      }
      builder.withMosaic { assertEquals(subject, "new singlenew multinew source") }
    }

  private class BorrowedSource : AutoCloseable {
    var closes = 0

    override fun close() {
      closes++
    }
  }

  @Test fun `Canvas sources stay caller owned across all overloads and failed executions`() =
    runTest {
      val resource = BorrowedSource()
      val key = CanvasKey(AutoCloseable::class, "key")
      val builder =
        mosaicBuilder()
          .withCanvasSource(resource)
          .withCanvasSource(AutoCloseable::class, resource)
          .withCanvasSource("inferred", resource)
          .withCanvasSource(AutoCloseable::class, "explicit", resource)
          .withCanvasSource(key, resource)
      kotlin.test.assertFailsWith<IllegalStateException> {
        builder.withMosaic {
          kotlin.test.assertSame(resource, source<BorrowedSource>())
          kotlin.test.assertSame(resource, source<AutoCloseable>())
          kotlin.test.assertSame(resource, source<BorrowedSource>("inferred"))
          kotlin.test.assertSame(resource, source<AutoCloseable>("explicit"))
          kotlin.test.assertSame(resource, source(key))
          canvas.close()
          assertEquals(0, resource.closes)
          error("test failure")
        }
      }
      assertEquals(0, resource.closes)
      builder.withMosaic { canvas.close() }
      assertEquals(0, resource.closes)
      resource.close()
      assertEquals(1, resource.closes)
    }
}
