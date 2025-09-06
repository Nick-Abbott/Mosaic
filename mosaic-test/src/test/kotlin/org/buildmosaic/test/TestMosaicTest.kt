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

import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.MosaicRegistry
import org.buildmosaic.core.MosaicRequest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for TestMosaic constructor and basic properties.
 */
@Suppress("LargeClass")
class TestMosaicTest {
  private lateinit var registry: MosaicRegistry
  private lateinit var request: MosaicRequest
  private lateinit var testMosaic: TestMosaic

  @BeforeTest
  fun setUp() {
    registry = MosaicRegistry()
    request = MockMosaicRequest()
    testMosaic = TestMosaic(Mosaic(registry, request))
  }

  @Test
  fun `should return request property correctly`() {
    assertEquals(request, testMosaic.request)
  }

  @Test
  fun `should return tiles when called`() {
    registry.register(TestSingleTile::class) { TestSingleTile(it) }
    registry.register(TestMultiTile::class) { TestMultiTile(it) }

    assertIs<TestSingleTile>(testMosaic.getTile(TestSingleTile::class))
    assertIs<TestSingleTile>(testMosaic.getTile<TestSingleTile>())
    assertIs<TestMultiTile>(testMosaic.getTile(TestMultiTile::class))
    assertIs<TestMultiTile>(testMosaic.getTile<TestMultiTile>())
  }

  @Test
  fun `should assert equals for SingleTile with KClass`() =
    runTest {
      registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

      val testData = "test-data"
      val customMessage = "Custom message"

      testMosaic.assertEquals(TestSingleTile::class, testData)
      testMosaic.assertEquals(TestSingleTile::class, testData, customMessage)

      assertFailsWith<AssertionError> { testMosaic.assertEquals(TestSingleTile::class, "wrong-data") }

      try {
        testMosaic.assertEquals(TestSingleTile::class, "wrong-data", customMessage)
        fail("Should have failed")
      } catch (e: AssertionError) {
        assertNotNull(e.message)
        assertTrue(e.message!!.contains(customMessage))
      }
    }

  @Test
  fun `should assert equals for MultiTile with KClass`() =
    runTest {
      registry.register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }

      val keys = listOf("key1", "key2")
      val expected = mapOf("key1" to "data-for-key1", "key2" to "data-for-key2")
      val wrongData = mapOf("key1" to "wrong-data", "key2" to "wrong-data")
      val customMessage = "Custom message"

      testMosaic.assertEquals(TestMultiTile::class, keys, expected)
      testMosaic.assertEquals(TestMultiTile::class, keys, expected, customMessage)

      assertFailsWith<AssertionError> { testMosaic.assertEquals(TestMultiTile::class, keys, wrongData) }

      try {
        testMosaic.assertEquals(TestMultiTile::class, keys, wrongData, customMessage)
        fail("Should have failed")
      } catch (e: AssertionError) {
        assertNotNull(e.message)
        assertTrue(e.message!!.contains(customMessage))
      }
    }

  @Test
  fun `should assert throws for SingleTile with KClass`() =
    runTest {
      registry.register(TestErrorSingleTile::class) { mosaic -> TestErrorSingleTile(mosaic) }
      registry.register(TestSingleTile::class) { mosaic -> TestSingleTile(mosaic) }

      val customMessage = "Custom message"

      testMosaic.assertThrows(TestErrorSingleTile::class, TestException::class)
      testMosaic.assertThrows(TestErrorSingleTile::class, TestException::class, customMessage)

      assertFailsWith<AssertionError> { testMosaic.assertThrows(TestSingleTile::class, TestException::class) }
      try {
        testMosaic.assertThrows(TestSingleTile::class, TestException::class, customMessage)
        fail("Should have failed")
      } catch (e: AssertionError) {
        assertNotNull(e.message)
        assertTrue(e.message!!.contains(customMessage))
      }

      assertFailsWith<AssertionError> { testMosaic.assertThrows(TestErrorSingleTile::class, RuntimeException::class) }
      try {
        testMosaic.assertThrows(TestErrorSingleTile::class, RuntimeException::class, customMessage)
        fail("Should have failed")
      } catch (e: AssertionError) {
        assertNotNull(e.message)
        assertTrue(e.message!!.contains(customMessage))
      }
    }

  @Test
  fun `should assert throws for MultiTile with KClass`() =
    runTest {
      registry.register(TestErrorMultiTile::class) { mosaic -> TestErrorMultiTile(mosaic) }
      registry.register(TestMultiTile::class) { mosaic -> TestMultiTile(mosaic) }

      val keys = listOf("key1")
      val customMessage = "Custom message"

      testMosaic.assertThrows(TestErrorMultiTile::class, keys, TestException::class)
      testMosaic.assertThrows(TestErrorMultiTile::class, keys, TestException::class, customMessage)

      assertFailsWith<AssertionError> { testMosaic.assertThrows(TestMultiTile::class, keys, RuntimeException::class) }
      try {
        testMosaic.assertThrows(TestMultiTile::class, keys, RuntimeException::class, customMessage)
        fail("Should have failed")
      } catch (e: AssertionError) {
        assertNotNull(e.message)
        assertTrue(e.message!!.contains(customMessage))
      }
      assertFailsWith<AssertionError> {
        testMosaic.assertThrows(TestErrorMultiTile::class, keys, NullPointerException::class)
      }
      try {
        testMosaic.assertThrows(TestErrorMultiTile::class, keys, NullPointerException::class, customMessage)
        fail("Should have failed")
      } catch (e: AssertionError) {
        assertNotNull(e.message)
        assertTrue(e.message!!.contains(customMessage))
      }
    }
}
