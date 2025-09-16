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
import org.buildmosaic.core.multiTile
import org.buildmosaic.core.singleTile
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
      assertIs<TestMosaic>(mosaicBuilder().build())
    }

  @Test
  fun `registers successful mock single tiles`() =
    runTest {
      val singleTileData = "mocked-data"
      val intTileData = 123
      val booleanTileData = false

      val testMosaic =
        mosaicBuilder()
          .withMockTile(testSingleTile, singleTileData)
          .withMockTile(testIntTile, intTileData)
          .withMockTile(testBooleanTile, booleanTileData)
          .build()

      testMosaic.assertEquals(testSingleTile, singleTileData)
      testMosaic.assertEquals(testIntTile, intTileData)
      testMosaic.assertEquals(testBooleanTile, booleanTileData)
    }

  @Test
  fun `registers successful mock multi tiles`() =
    runTest {
      val multiTileData = mapOf("a" to "A", "b" to "B")
      val intMultiTileData = mapOf(1 to "one", 2 to "two")

      val testMosaic =
        mosaicBuilder()
          .withMockTile(testMultiTile, multiTileData)
          .withMockTile(testIntMultiTile, intMultiTileData)
          .build()

      testMosaic.assertEquals(testMultiTile, multiTileData.keys, multiTileData)
      testMosaic.assertEquals(testIntMultiTile, intMultiTileData.keys, intMultiTileData)
    }

  @Test
  fun `registers failed single tiles`() =
    runTest {
      val exception = IllegalStateException("test error")
      val runtimeException = RuntimeException("runtime error")

      val testMosaic =
        mosaicBuilder()
          .withFailedTile(testSingleTile, exception)
          .withFailedTile(testIntTile, runtimeException)
          .build()

      testMosaic.assertThrows(testSingleTile, IllegalStateException::class)
      testMosaic.assertThrows(testIntTile, RuntimeException::class)
    }

  @Test
  fun `registers failed multi tiles`() =
    runTest {
      val exception = IllegalStateException("multi tile error")
      val runtimeException = RuntimeException("multi runtime error")

      val testMosaic =
        mosaicBuilder()
          .withFailedTile(testMultiTile, exception)
          .withFailedTile(testIntMultiTile, runtimeException)
          .build()

      testMosaic.assertThrows(testMultiTile, listOf("a"), IllegalStateException::class)
      testMosaic.assertThrows(testIntMultiTile, listOf(1), RuntimeException::class)
    }

  @Test
  fun `registers delayed single tiles`() =
    runTest {
      val singleDelay = 50L
      val singleData = "delayed-single"
      val intDelay = 75L
      val intData = 999

      val testMosaic =
        mosaicBuilder()
          .withDelayedTile(testSingleTile, singleData, singleDelay)
          .withDelayedTile(testIntTile, intData, intDelay)
          .build()

      var singleResult: String? = null
      var intResult: Int? = null

      launch {
        val workDuration =
          testScheduler.timeSource.measureTime {
            singleResult = testMosaic.compose(testSingleTile)
            intResult = testMosaic.compose(testIntTile)
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

  @Test
  fun `registers delayed multi tiles`() =
    runTest {
      val multiDelay = 100L
      val multiData = mapOf("x" to "X", "y" to "Y")
      val intMultiDelay = 150L
      val intMultiData = mapOf(10 to "ten", 20 to "twenty")

      val testMosaic =
        mosaicBuilder()
          .withDelayedTile(testMultiTile, multiData, multiDelay)
          .withDelayedTile(testIntMultiTile, intMultiData, intMultiDelay)
          .build()

      var multiResult: Map<String, String>? = null
      var intMultiResult: Map<Int, String>? = null

      launch {
        val workDuration =
          testScheduler.timeSource.measureTime {
            multiResult = testMosaic.compose(testMultiTile, multiData.keys)
            intMultiResult = testMosaic.compose(testIntMultiTile, intMultiData.keys)
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

  @Test
  fun `registers custom single tiles`() =
    runTest {
      val testMosaic =
        mosaicBuilder()
          .withCustomTile(testSingleTile) { "custom-single" }
          .withCustomTile(testIntTile) { 777 }
          .build()

      testMosaic.assertEquals(testSingleTile, "custom-single")
      testMosaic.assertEquals(testIntTile, 777)
    }

  @Test
  fun `registers custom multi tiles`() =
    runTest {
      val inputStringKeys = setOf("a", "b")
      val inputIntKeys = setOf(1, 2)

      val testMosaic =
        mosaicBuilder()
          .withCustomTile(testMultiTile) { keys ->
            keys.associateWith { it.uppercase() + "-custom" }
          }
          .withCustomTile(testIntMultiTile) { keys ->
            keys.associateWith { "custom-${it * 10}" }
          }
          .build()

      val expectedStringResult = inputStringKeys.associateWith { it.uppercase() + "-custom" }
      val expectedIntResult = inputIntKeys.associateWith { "custom-${it * 10}" }

      testMosaic.assertEquals(testMultiTile, inputStringKeys, expectedStringResult)
      testMosaic.assertEquals(testIntMultiTile, inputIntKeys, expectedIntResult)
    }

  @Test
  fun `supports dependency injection with KClass`() =
    runTest {
      data class TestService(val name: String)
      val testService = TestService("test-service")

      val testMosaic =
        mosaicBuilder()
          .withCanvasSource(TestService::class, testService)
          .build()

      // We can't directly test injection retrieval through TestMosaic,
      // but we can verify the mosaicBuilder() accepts the injection without error
      assertIs<TestMosaic>(testMosaic)
    }

  @Test
  fun `supports dependency injection with reified type`() =
    runTest {
      data class AnotherService(val value: Int)
      val anotherService = AnotherService(42)

      val testMosaic =
        mosaicBuilder()
          .withCanvasSource(anotherService)
          .build()

      // We can't directly test injection retrieval through TestMosaic,
      // but we can verify the mosaicBuilder() accepts the injection without error
      assertIs<TestMosaic>(testMosaic)
    }

  @Test
  fun `mosaicBuilder() methods return mosaicBuilder() for chaining`() =
    runTest {
      val result =
        mosaicBuilder()
          .withMockTile(testSingleTile, "test")
          .withMockTile(testMultiTile, mapOf("a" to "A"))
          .withFailedTile(testIntTile, RuntimeException("error"))
          .withDelayedTile(testBooleanTile, true, 100L)
          .withCanvasSource(String::class, "injected")

      assertIs<TestMosaicBuilder>(result)
      assertIs<TestMosaic>(result.build())
    }

  @Test
  fun `handles null values in mock tiles`() =
    runTest {
      val nullableTile = singleTile<String?> { null }
      val testMosaic =
        mosaicBuilder()
          .withMockTile(nullableTile, null)
          .build()

      testMosaic.assertEquals(nullableTile, null)
    }

  @Test
  fun `handles empty maps in multi tiles`() =
    runTest {
      val emptyMap = emptyMap<String, String>()
      val testMosaic =
        mosaicBuilder()
          .withMockTile(testMultiTile, emptyMap)
          .build()

      testMosaic.assertEquals(testMultiTile, emptySet(), emptyMap)
    }

  @Test
  fun `supports complex data types`() =
    runTest {
      data class ComplexData(val id: Int, val name: String, val tags: List<String>)
      val complexTile = singleTile { ComplexData(1, "test", listOf("tag1", "tag2")) }
      val complexData = ComplexData(99, "mocked", listOf("mock", "test"))

      val testMosaic =
        mosaicBuilder()
          .withMockTile(complexTile, complexData)
          .build()

      testMosaic.assertEquals(complexTile, complexData)
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

      val testMosaic =
        mosaicBuilder()
          .withMockTile(baseTile, "mocked-base")
          .withCustomTile(composedTile) {
            val base = compose(baseTile)
            "custom-composed-$base"
          }
          .build()

      testMosaic.assertEquals(composedTile, "custom-composed-mocked-base")
    }

  @Test
  fun `handles multiple registrations of same tile type`() =
    runTest {
      // Last registration should win
      val testMosaic =
        mosaicBuilder()
          .withMockTile(testSingleTile, "first")
          .withMockTile(testSingleTile, "second")
          .withMockTile(testSingleTile, "third")
          .build()

      testMosaic.assertEquals(testSingleTile, "third")
    }
}
