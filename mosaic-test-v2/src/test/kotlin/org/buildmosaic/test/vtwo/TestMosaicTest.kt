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

package org.buildmosaic.test.vtwo

import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.vtwo.multiTile
import org.buildmosaic.core.vtwo.singleTile
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for TestMosaic constructor and basic properties.
 */
@Suppress("LargeClass", "FunctionMaxLength")
class TestMosaicTest {
  private lateinit var testMosaic: TestMosaic

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

  @BeforeTest
  fun setUp() {
    testMosaic = TestMosaicBuilder().build()
  }

  @Test
  fun `should get single tile values`() =
    runTest {
      val testData = "mocked-data"
      val intData = 123

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(testSingleTile, testData)
          .withMockTile(testIntTile, intData)
          .build()

      assertEquals(testData, mosaic.compose(testSingleTile))
      assertEquals(intData, mosaic.compose(testIntTile))
    }

  @Test
  fun `should get multi tile values with collection`() =
    runTest {
      val keys = listOf("key1", "key2")
      val expected = mapOf("key1" to "value1", "key2" to "value2")

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(testMultiTile, expected)
          .build()

      assertEquals(expected, mosaic.compose(testMultiTile, keys))
    }

  @Test
  fun `should get multi tile values with one key`() =
    runTest {
      val expected = mapOf("a" to "A", "b" to "B", "c" to "C")

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(testMultiTile, expected)
          .build()

      assertEquals(expected["a"]!!, mosaic.compose(testMultiTile, "a"))
    }

  @Test
  fun `should assert equals for single tile`() =
    runTest {
      val testData = "expected-data"

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(testSingleTile, testData)
          .build()

      mosaic.assertEquals(testSingleTile, testData)
    }

  @Test
  fun `should assert equals for single tile with custom message`() =
    runTest {
      val testData = "expected-data"
      val customMessage = "Custom assertion message"

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(testSingleTile, testData)
          .build()

      mosaic.assertEquals(testSingleTile, testData, customMessage)
    }

  @Test
  fun `should fail assert equals for single tile with wrong data`() =
    runTest {
      val testData = "expected-data"
      val wrongData = "wrong-data"

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(testSingleTile, testData)
          .build()

      assertFailsWith<AssertionError> {
        mosaic.assertEquals(testSingleTile, wrongData)
      }
    }

  @Test
  fun `should fail assert equals for single tile with custom message`() =
    runTest {
      val testData = "expected-data"
      val wrongData = "wrong-data"
      val customMessage = "Custom failure message"

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(testSingleTile, testData)
          .build()

      try {
        mosaic.assertEquals(testSingleTile, wrongData, customMessage)
        fail("Should have failed")
      } catch (e: AssertionError) {
        assertNotNull(e.message)
        assertTrue(e.message!!.contains(customMessage))
      }
    }

  @Test
  fun `should assert equals for multi tile with collection`() =
    runTest {
      val keys = listOf("key1", "key2")
      val expected = mapOf("key1" to "data-for-key1", "key2" to "data-for-key2")

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(testMultiTile, expected)
          .build()

      mosaic.assertEquals(testMultiTile, keys, expected)
    }

  @Test
  fun `should assert equals for multi tile with list and custom message`() =
    runTest {
      val keys = listOf("key1", "key2")
      val expected = mapOf("key1" to "data-for-key1", "key2" to "data-for-key2")
      val customMessage = "Multi tile assertion message"

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(testMultiTile, expected)
          .build()

      mosaic.assertEquals(testMultiTile, keys, expected, customMessage)
    }

  @Test
  fun `should fail assert equals for multi tile with wrong data`() =
    runTest {
      val keys = listOf("key1", "key2")
      val expected = mapOf("key1" to "data-for-key1", "key2" to "data-for-key2")
      val wrongData = mapOf("key1" to "wrong-data", "key2" to "wrong-data")

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(testMultiTile, expected)
          .build()

      assertFailsWith<AssertionError> {
        mosaic.assertEquals(testMultiTile, keys, wrongData)
      }
    }

  @Test
  fun `should fail assert equals for multi tile with custom message`() =
    runTest {
      val keys = listOf("key1", "key2")
      val expected = mapOf("key1" to "data-for-key1", "key2" to "data-for-key2")
      val wrongData = mapOf("key1" to "wrong-data", "key2" to "wrong-data")
      val customMessage = "Multi tile failure message"

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(testMultiTile, expected)
          .build()

      try {
        mosaic.assertEquals(testMultiTile, keys, wrongData, customMessage)
        fail("Should have failed")
      } catch (e: AssertionError) {
        assertNotNull(e.message)
        assertTrue(e.message!!.contains(customMessage))
      }
    }

  @Test
  fun `should assert throws for single tile`() =
    runTest {
      val mosaic =
        TestMosaicBuilder()
          .withFailedTile(testErrorTile, TestException("Test error"))
          .build()

      mosaic.assertThrows(testErrorTile, TestException::class)
    }

  @Test
  fun `should assert throws for single tile with custom message`() =
    runTest {
      val customMessage = "Expected exception message"

      val mosaic =
        TestMosaicBuilder()
          .withFailedTile(testErrorTile, TestException("Test error"))
          .build()

      mosaic.assertThrows(testErrorTile, TestException::class, customMessage)
    }

  @Test
  fun `should fail assert throws for single tile with wrong exception`() =
    runTest {
      val mosaic =
        TestMosaicBuilder()
          .withMockTile(testSingleTile, "normal-data")
          .build()

      assertFailsWith<AssertionError> {
        mosaic.assertThrows(testSingleTile, RuntimeException::class)
      }
    }

  @Test
  fun `should fail assert throws for single tile with wrong exception and custom message`() =
    runTest {
      val customMessage = "Wrong exception message"

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(testSingleTile, "normal-data")
          .build()

      try {
        mosaic.assertThrows(testSingleTile, RuntimeException::class, customMessage)
        fail("Should have failed")
      } catch (e: AssertionError) {
        assertNotNull(e.message)
        assertTrue(e.message!!.contains(customMessage))
      }
    }

  @Test
  fun `should assert throws for multi tile`() =
    runTest {
      val keys = listOf("key1")

      val mosaic =
        TestMosaicBuilder()
          .withFailedTile(testErrorMultiTile, TestException("Multi error"))
          .build()

      mosaic.assertThrows(testErrorMultiTile, keys, TestException::class)
    }

  @Test
  fun `should fail assert throws for multi tile with wrong exception`() =
    runTest {
      val keys = listOf("key1")
      val expected = mapOf("key1" to "normal-data")

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(testMultiTile, expected)
          .build()

      assertFailsWith<AssertionError> {
        mosaic.assertThrows(testMultiTile, keys, RuntimeException::class)
      }
    }

  @Test
  fun `should handle null values in assertions`() =
    runTest {
      val nullableTile = singleTile<String?> { null }

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(nullableTile, null)
          .build()

      mosaic.assertEquals(nullableTile, null)
    }

  @Test
  fun `should handle empty collections in multi tile assertions`() =
    runTest {
      val emptyKeys = emptyList<String>()
      val emptyResult = emptyMap<String, String>()

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(testMultiTile, emptyResult)
          .build()

      mosaic.assertEquals(testMultiTile, emptyKeys, emptyResult)
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

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(complexTile, complexData)
          .build()

      mosaic.assertEquals(complexTile, complexData)
    }

  @Test
  fun `should support different key types in multi tiles`() =
    runTest {
      val intKeys = listOf(1, 2, 3)
      val intExpected = mapOf(1 to "one", 2 to "two", 3 to "three")

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(testIntMultiTile, intExpected)
          .build()

      mosaic.assertEquals(testIntMultiTile, intKeys, intExpected)
    }

  @Test
  fun `should handle mixed success and failure scenarios`() =
    runTest {
      val successData = "success"

      val mosaic =
        TestMosaicBuilder()
          .withMockTile(testSingleTile, successData)
          .withFailedTile(testErrorTile, RuntimeException("Expected failure"))
          .build()

      // Success case
      mosaic.assertEquals(testSingleTile, successData)

      // Failure case
      mosaic.assertThrows(testErrorTile, RuntimeException::class)
    }
}

// Test exception class for testing error scenarios
class TestException(message: String) : Exception(message)
