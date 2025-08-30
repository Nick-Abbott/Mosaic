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

@file:Suppress("LargeClass")

package com.abbott.mosaic.test

import com.abbott.mosaic.MosaicRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TestMosaicBuilderTest : BaseTestMosaicTest() {
  private val builder = TestMosaicBuilder()

  @Test
  fun `builds mosaic with mock single tile`() =
    runTestMosaicTest {
      val testMosaic = builder.withMockTile(TestSingleTile::class, "data").build()
      testMosaic.assertEquals(tileClass = TestSingleTile::class, expected = "data")
    }

  @Test
  fun `failed single tile throws`() =
    runTestMosaicTest {
      val testMosaic =
        builder
          .withFailedTile(TestSingleTile::class, IllegalStateException("boom"))
          .build()
      testMosaic.assertThrows(tileClass = TestSingleTile::class, expectedException = IllegalStateException::class.java)
    }

  @Test
  fun `delayed single tile returns data`() =
    runTestMosaicTest {
      val testMosaic = builder.withDelayedTile(TestSingleTile::class, "delay", 10).build()
      testMosaic.assertEquals(tileClass = TestSingleTile::class, expected = "delay")
    }

  @Test
  fun `custom single tile uses lambda`() =
    runTestMosaicTest {
      val testMosaic = builder.withCustomTile(TestSingleTile::class) { "custom" }.build()
      testMosaic.assertEquals(tileClass = TestSingleTile::class, expected = "custom")
    }

  @Test
  fun `builds mosaic with mock multi tile`() =
    runTestMosaicTest {
      val data = mapOf("a" to "A", "b" to "B")
      val testMosaic = builder.withMockTile(TestMultiTile::class, data).build()
      testMosaic.assertEquals(tileClass = TestMultiTile::class, keys = listOf("a"), expected = mapOf("a" to "A"))
    }

  @Test
  fun `failed multi tile throws`() =
    runTestMosaicTest {
      val testMosaic =
        builder
          .withFailedTile(TestMultiTile::class, IllegalStateException("fail"))
          .build()
      testMosaic.assertThrows(
        tileClass = TestMultiTile::class,
        keys = listOf("a"),
        expectedException = IllegalStateException::class.java,
      )
    }

  @Test
  fun `delayed multi tile returns data`() =
    runTestMosaicTest {
      val data = mapOf("a" to "A")
      val testMosaic = builder.withDelayedTile(TestMultiTile::class, data, 10).build()
      testMosaic.assertEquals(tileClass = TestMultiTile::class, keys = listOf("a"), expected = data)
    }

  @Test
  fun `custom multi tile uses lambda`() =
    runTestMosaicTest {
      val testMosaic =
        builder
          .withCustomTile(TestMultiTile::class) { keys -> keys.associateWith { it.uppercase() } }
          .build()
      testMosaic.assertEquals(
        tileClass = TestMultiTile::class,
        keys = listOf("a"),
        expected = mapOf("a" to "A"),
      )
    }

  @Test
  fun `allows custom request`() {
    val customRequest = object : MosaicRequest {}
    val testMosaic = builder.withRequest(customRequest).build()
    assertEquals(customRequest, testMosaic.request)
  }
}
