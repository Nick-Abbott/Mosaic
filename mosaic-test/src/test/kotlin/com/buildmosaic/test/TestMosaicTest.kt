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

@file:Suppress("FunctionMaxLength", "SwallowedException", "LargeClass", "MaxLineLength")

package com.buildmosaic.test

import com.buildmosaic.core.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

/**
 * Tests for TestMosaic constructor and basic properties.
 */
class TestMosaicTest : BaseTestMosaicTest() {
  @Test
  fun `should create TestMosaic with valid parameters`() {
    // Given
    val mockTiles = mapOf<KClass<*>, Tile>()

    // When
    val testMosaic = TestMosaic(mosaic, mockTiles)

    // Then
    assertNotNull(testMosaic)
    assertEquals(request, testMosaic.request)
  }

  @Test
  fun `should return request property correctly`() {
    // Given
    val testMosaic = TestMosaic(mosaic, emptyMap())

    // When
    val result = testMosaic.request

    // Then
    assertEquals(request, result)
  }

  @Test
  fun `should return mock tiles correctly`() {
    // Given
    val mockTiles = mapOf<KClass<*>, Tile>()
    val testMosaic = TestMosaic(mosaic, mockTiles)

    // When
    val result = testMosaic.getMockTiles()

    // Then
    assertEquals(mockTiles, result)
  }

  @Test
  fun `should get tile with KClass parameter`() {
    // Given
    val testMosaic = TestMosaic(mosaic, emptyMap())
    registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

    // When
    val tile = testMosaic.getTile(TestSingleTile::class)

    // Then
    assertNotNull(tile)
  }

  @Test
  fun `should get tile with reified type parameter`() {
    // Given
    val testMosaic = TestMosaic(mosaic, emptyMap())
    registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

    // When
    val tile = testMosaic.getTile<TestSingleTile>()

    // Then
    assertNotNull(tile)
  }

  @Test
  fun `should assert equals for SingleTile with KClass - success case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

      // When & Then
      testMosaic.assertEquals(tileClass = TestSingleTile::class, expected = "test-data")
    }

  @Test
  fun `should assert equals for SingleTile with KClass - failure case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertEquals(tileClass = TestSingleTile::class, expected = "wrong-data")
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - assertion failed as expected
      }
    }

  @Test
  fun `should assert equals for SingleTile with KClass and message - success case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

      // When & Then
      testMosaic.assertEquals(tileClass = TestSingleTile::class, expected = "test-data", message = "Custom message")
    }

  @Test
  fun `should assert equals for SingleTile with KClass and message - failure case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertEquals(tileClass = TestSingleTile::class, expected = "wrong-data", message = "Custom message")
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - assertion failed as expected
      }
    }

  @Test
  fun `should assert equals for SingleTile with reified type - success case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

      // When & Then
      testMosaic.assertEquals<TestSingleTile, String>("test-data")
    }

  @Test
  fun `should assert equals for SingleTile with reified type - failure case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertEquals<TestSingleTile, String>("wrong-data")
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - assertion failed as expected
      }
    }

  @Test
  fun `should assert equals for SingleTile with reified type and message - success case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

      // When & Then
      testMosaic.assertEquals<TestSingleTile, String>("test-data", "Custom message")
    }

  @Test
  fun `should assert equals for SingleTile with reified type and message - failure case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertEquals<TestSingleTile, String>("wrong-data", "Custom message")
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - assertion failed as expected
      }
    }

  @Test
  fun `should assert equals for MultiTile with KClass - success case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }

      // When & Then
      testMosaic.assertEquals(
        tileClass = TestMultiTile::class,
        keys = listOf("key1", "key2"),
        expected = mapOf("key1" to "data-for-key1", "key2" to "data-for-key2"),
      )
    }

  @Test
  fun `should assert equals for MultiTile with KClass - failure case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertEquals(
          tileClass = TestMultiTile::class,
          keys = listOf("key1"),
          expected = mapOf("key1" to "wrong-data"),
        )
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - assertion failed as expected
      }
    }

  @Test
  fun `should assert equals for MultiTile with KClass and message - success case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }

      // When & Then
      testMosaic.assertEquals(
        tileClass = TestMultiTile::class,
        keys = listOf("key1"),
        expected = mapOf("key1" to "data-for-key1"),
        message = "Custom message",
      )
    }

  @Test
  fun `should assert equals for MultiTile with KClass and message - failure case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertEquals(
          tileClass = TestMultiTile::class,
          keys = listOf("key1"),
          expected = mapOf("key1" to "wrong-data"),
          message = "Custom message",
        )
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - assertion failed as expected
      }
    }

  @Test
  fun `should assert equals for MultiTile with reified type - success case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }

      // When & Then
      testMosaic.assertEquals<TestMultiTile, String>(
        keys = listOf("key1"),
        expected = mapOf("key1" to "data-for-key1"),
      )
    }

  @Test
  fun `should assert equals for MultiTile with reified type - failure case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertEquals<TestMultiTile, String>(
          keys = listOf("key1"),
          expected = mapOf("key1" to "wrong-data"),
        )
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - assertion failed as expected
      }
    }

  @Test
  fun `should assert equals for MultiTile with reified type and message - success case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }

      // When & Then
      testMosaic.assertEquals<TestMultiTile, String>(
        keys = listOf("key1"),
        expected = mapOf("key1" to "data-for-key1"),
        message = "Custom message",
      )
    }

  @Test
  fun `should assert equals for MultiTile with reified type and message - failure case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertEquals<TestMultiTile, String>(
          keys = listOf("key1"),
          expected = mapOf("key1" to "wrong-data"),
          message = "Custom message",
        )
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - assertion failed as expected
      }
    }

  @Test
  fun `should assert throws for SingleTile with KClass - success case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestErrorSingleTile::class) { mosaic -> TestErrorSingleTile(mosaic) }

      // When & Then
      testMosaic.assertThrows(TestErrorSingleTile::class, RuntimeException::class.java)
    }

  @Test
  fun `should assert throws for SingleTile with KClass - wrong exception type`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertThrows(TestSingleTile::class, RuntimeException::class.java)
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - no exception was thrown
      }
    }

  @Test
  fun `should assert throws for SingleTile with KClass and message - success case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestErrorSingleTile::class) { mosaic -> TestErrorSingleTile(mosaic) }

      // When & Then
      testMosaic.assertThrows(TestErrorSingleTile::class, RuntimeException::class.java, "Custom message")
    }

  @Test
  fun `should assert throws for SingleTile with KClass and message - wrong exception type`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestErrorSingleTile::class) { mosaic -> TestErrorSingleTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertThrows(TestErrorSingleTile::class, IllegalArgumentException::class.java, "Custom message")
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - wrong exception type
      }
    }

  @Test
  fun `should assert throws for SingleTile with KClass and message - no exception thrown`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertThrows(TestSingleTile::class, RuntimeException::class.java, "Custom message")
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - no exception was thrown
      }
    }

  @Test
  fun `should assert throws for SingleTile with reified type - success case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestErrorSingleTile::class) { mosaic -> TestErrorSingleTile(mosaic) }

      // When & Then
      testMosaic.assertThrows<TestErrorSingleTile>(RuntimeException::class.java)
    }

  @Test
  fun `should assert throws for SingleTile with reified type - wrong exception type`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestErrorSingleTile::class) { mosaic -> TestErrorSingleTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertThrows<TestErrorSingleTile>(IllegalArgumentException::class.java)
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - wrong exception type
      }
    }

  @Test
  fun `should assert throws for SingleTile with reified type - no exception thrown`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertThrows<TestSingleTile>(RuntimeException::class.java)
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - no exception was thrown
      }
    }

  @Test
  fun `should assert throws for SingleTile with reified type and message - success case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestErrorSingleTile::class) { mosaic -> TestErrorSingleTile(mosaic) }

      // When & Then
      testMosaic.assertThrows<TestErrorSingleTile>(RuntimeException::class.java, "Custom message")
    }

  @Test
  fun `should assert throws for SingleTile with reified type and message - wrong exception type`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestErrorSingleTile::class) { mosaic -> TestErrorSingleTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertThrows<TestErrorSingleTile>(IllegalArgumentException::class.java, "Custom message")
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - wrong exception type
      }
    }

  @Test
  fun `should assert throws for SingleTile with reified type and message - no exception thrown`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertThrows<TestSingleTile>(RuntimeException::class.java, "Custom message")
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - no exception was thrown
      }
    }

  @Test
  fun `should assert throws for MultiTile with KClass - success case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestErrorMultiTile::class) { mosaic -> TestErrorMultiTile(mosaic) }

      // When & Then
      testMosaic.assertThrows(TestErrorMultiTile::class, listOf("key1"), RuntimeException::class.java)
    }

  @Test
  fun `should assert throws for MultiTile with KClass - wrong exception type`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestErrorMultiTile::class) { mosaic -> TestErrorMultiTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertThrows(TestErrorMultiTile::class, listOf("key1"), IllegalArgumentException::class.java)
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - wrong exception type
      }
    }

  @Test
  fun `should assert throws for MultiTile with KClass - no exception thrown`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertThrows(TestMultiTile::class, listOf("key1"), RuntimeException::class.java)
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - no exception was thrown
      }
    }

  @Test
  fun `should assert throws for MultiTile with KClass and message - success case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestErrorMultiTile::class) { mosaic -> TestErrorMultiTile(mosaic) }

      // When & Then
      testMosaic.assertThrows(TestErrorMultiTile::class, listOf("key1"), RuntimeException::class.java, "Custom message")
    }

  @Test
  fun `should assert throws for MultiTile with KClass and message - wrong exception type`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestErrorMultiTile::class) { mosaic -> TestErrorMultiTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertThrows(
          TestErrorMultiTile::class,
          listOf("key1"),
          IllegalArgumentException::class.java,
          "Custom message",
        )
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - wrong exception type
      }
    }

  @Test
  fun `should assert throws for MultiTile with KClass and message - no exception thrown`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertThrows(TestMultiTile::class, listOf("key1"), RuntimeException::class.java, "Custom message")
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - no exception was thrown
      }
    }

  @Test
  fun `should assert throws for MultiTile with reified type - success case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestErrorMultiTile::class) { mosaic -> TestErrorMultiTile(mosaic) }

      // When & Then
      testMosaic.assertThrows<TestErrorMultiTile, String>(listOf("key1"), RuntimeException::class.java)
    }

  @Test
  fun `should assert throws for MultiTile with reified type - wrong exception type`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestErrorMultiTile::class) { mosaic -> TestErrorMultiTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertThrows<TestErrorMultiTile, String>(listOf("key1"), IllegalArgumentException::class.java)
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - wrong exception type
      }
    }

  @Test
  fun `should assert throws for MultiTile with reified type - no exception thrown`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertThrows<TestMultiTile, String>(listOf("key1"), RuntimeException::class.java)
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - no exception was thrown
      }
    }

  @Test
  fun `should assert throws for MultiTile with reified type and message - success case`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestErrorMultiTile::class) { mosaic -> TestErrorMultiTile(mosaic) }

      // When & Then
      testMosaic.assertThrows<TestErrorMultiTile, String>(
        listOf("key1"),
        RuntimeException::class.java,
        "Custom message",
      )
    }

  @Test
  fun `should assert throws for MultiTile with reified type and message - wrong exception type`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestErrorMultiTile::class) { mosaic -> TestErrorMultiTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertThrows<TestErrorMultiTile, String>(
          listOf("key1"),
          IllegalArgumentException::class.java,
          "Custom message",
        )
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - wrong exception type
      }
    }

  @Test
  fun `should assert throws for MultiTile with reified type and message - no exception thrown`() =
    runTestMosaicTest {
      // Given
      val testMosaic = TestMosaic(mosaic, emptyMap())
      registry.register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }

      // When & Then
      try {
        testMosaic.assertThrows<TestMultiTile, String>(listOf("key1"), RuntimeException::class.java, "Custom message")
        org.junit.jupiter.api.Assertions.fail("Expected assertion to fail")
      } catch (e: AssertionError) {
        // Expected - no exception was thrown
      }
    }

  @Test
  fun `should call getMockTiles directly`() {
    // Given
    val mockTiles = mapOf<KClass<*>, Tile>()
    val testMosaic = TestMosaic(mosaic, mockTiles)

    // When
    val result = testMosaic.getMockTiles()

    // Then
    assertEquals(mockTiles, result)
  }

  @Test
  fun `should call getTile with KClass directly`() {
    // Given
    registry.register(TestSingleTile::class) { TestSingleTile(it) }

    // When
    val tile = testMosaic.getTile(TestSingleTile::class)

    // Then
    assertNotNull(tile)
  }

  @Test
  fun `should call reified getTile directly`() {
    // Given
    registry.register(TestSingleTile::class) { TestSingleTile(it) }

    // When
    val tile = testMosaic.getTile<TestSingleTile>()

    // Then
    assertNotNull(tile)
  }

  @Test
  fun `should call getTile with MultiTile KClass directly`() {
    // Given
    registry.register(TestMultiTile::class) { TestMultiTile(it) }

    // When
    val tile = testMosaic.getTile(TestMultiTile::class)

    // Then
    assertNotNull(tile)
  }

  @Test
  fun `should call reified getTile with MultiTile directly`() {
    // Given
    registry.register(TestMultiTile::class) { TestMultiTile(it) }

    // When
    val tile = testMosaic.getTile<TestMultiTile>()

    // Then
    assertNotNull(tile)
  }

  @Test
  fun `should execute assertEquals with KClass directly`() =
    runTestMosaicTest {
      // Given
      registry.register(TestSingleTile::class) { TestSingleTile(mosaic) }

      // When & Then
      testMosaic.assertEquals(tileClass = TestSingleTile::class, expected = "test-data")
    }

  @Test
  fun `should execute assertEquals with reified type directly`() =
    runTestMosaicTest {
      // Given
      registry.register(TestSingleTile::class) { TestSingleTile(mosaic) }

      // When & Then
      testMosaic.assertEquals<TestSingleTile, String>("test-data")
    }

  @Test
  fun `should execute assertThrows with KClass directly`() =
    runTestMosaicTest {
      // Given
      registry.register(TestErrorSingleTile::class) { TestErrorSingleTile(mosaic) }

      // When & Then
      testMosaic.assertThrows(tileClass = TestErrorSingleTile::class, expectedException = RuntimeException::class.java)
    }

  @Test
  fun `should execute assertThrows with reified type directly`() =
    runTestMosaicTest {
      // Given
      registry.register(TestErrorSingleTile::class) { TestErrorSingleTile(mosaic) }

      // When & Then
      testMosaic.assertThrows<TestErrorSingleTile>(expectedException = RuntimeException::class.java)
    }

  @Test
  fun `should execute assertEquals for MultiTile with KClass directly`() =
    runTestMosaicTest {
      // Given
      registry.register(TestMultiTile::class) { TestMultiTile(mosaic) }

      // When & Then
      testMosaic.assertEquals(
        tileClass = TestMultiTile::class,
        keys = listOf("key1"),
        expected = mapOf("key1" to "data-for-key1"),
      )
    }

  @Test
  fun `should execute assertEquals for MultiTile with reified type directly`() =
    runTestMosaicTest {
      // Given
      registry.register(TestMultiTile::class) { TestMultiTile(mosaic) }

      // When & Then
      testMosaic.assertEquals<TestMultiTile, String>(keys = listOf("key1"), expected = mapOf("key1" to "data-for-key1"))
    }

  @Test
  fun `should execute assertThrows for MultiTile with KClass directly`() =
    runTestMosaicTest {
      // Given
      registry.register(TestErrorMultiTile::class) { TestErrorMultiTile(mosaic) }

      // When & Then
      testMosaic.assertThrows(
        tileClass = TestErrorMultiTile::class,
        keys = listOf("key1"),
        expectedException = RuntimeException::class.java,
      )
    }

  @Test
  fun `should execute assertThrows for MultiTile with reified type directly`() =
    runTestMosaicTest {
      // Given
      registry.register(TestErrorMultiTile::class) { TestErrorMultiTile(mosaic) }

      // When & Then
      testMosaic.assertThrows<TestErrorMultiTile, String>(
        keys = listOf("key1"),
        expectedException = RuntimeException::class.java,
      )
    }
}
