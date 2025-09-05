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

package com.buildmosaic.test

import com.buildmosaic.core.MosaicRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

@Suppress("LargeClass")
class TestMosaicBuilderTest {
  private val builder = TestMosaicBuilder()

  @Test
  fun `builds a test mosaic`() =
    runTest {
      assertIs<TestMosaic>(builder.build())
    }

  @Test
  fun `registers successful mock tiles`() =
    runTest {
      val singleTileData = "data"
      val multiTileData = mapOf("a" to "A")
      val testMosaic =
        builder
          .withMockTile(TestSingleTile::class, singleTileData)
          .withMockTile(TestMultiTile::class, multiTileData)
          .build()
      testMosaic.assertEquals(TestSingleTile::class, singleTileData)
      testMosaic.assertEquals(TestMultiTile::class, multiTileData.keys.toList(), multiTileData)
    }

  @Test
  fun `registers failed mock tiles`() =
    runTest {
      val testMosaic =
        builder
          .withFailedTile(TestSingleTile::class, IllegalStateException("boom"))
          .withFailedTile(TestMultiTile::class, IllegalStateException("boom"))
          .build()
      testMosaic.assertThrows(TestSingleTile::class, IllegalStateException::class)
      testMosaic.assertThrows(TestMultiTile::class, keys = listOf("a"), IllegalStateException::class)
    }

  @Test
  fun `registers delayed mock tiles`() =
    runTest {
      val singleDelay = 50L
      val singleData = "delay"
      val multiDelay = singleDelay + 100L
      val multiData = mapOf("a" to "A")

      val testMosaic =
        builder
          .withDelayedTile(TestSingleTile::class, singleData, singleDelay)
          .withDelayedTile(TestMultiTile::class, multiData, multiDelay)
          .build()

      var singleResult: String? = null
      var multiResult: Map<String, String>? = null
      launch {
        val workDuration =
          testScheduler.timeSource.measureTime {
            singleResult = testMosaic.getTile<TestSingleTile>().get()
            multiResult = testMosaic.getTile<TestMultiTile>().getByKeys(multiData.keys.toList())
          }
        assertEquals((singleDelay + multiDelay).milliseconds, workDuration)
      }

      testScheduler.runCurrent()
      testScheduler.advanceTimeBy(10.milliseconds)
      assertNull(singleResult)
      assertNull(multiResult)

      testScheduler.advanceTimeBy(singleDelay.milliseconds)
      assertEquals(singleData, singleResult)
      assertNull(multiResult)

      testScheduler.advanceTimeBy(multiDelay.milliseconds)
      assertEquals(singleData, singleResult)
      assertEquals(multiData, multiResult)

      testScheduler.advanceUntilIdle()
    }

  @Test
  fun `registers custom mock tiles`() =
    runTest {
      val customSingleFunc = { "custom" }
      val customMultiFunc = { keys: List<String> -> keys.associateWith { it.uppercase() } }
      val inputList = listOf("a")

      val testMosaic =
        builder
          .withCustomTile(TestSingleTile::class, customSingleFunc)
          .withCustomTile(TestMultiTile::class, customMultiFunc)
          .build()

      testMosaic.assertEquals(TestSingleTile::class, customSingleFunc())
      testMosaic.assertEquals(TestMultiTile::class, inputList, customMultiFunc(inputList))
    }

  @Test
  fun `allows custom request`() =
    runTest {
      val customRequest = object : MosaicRequest {}
      val testMosaic = builder.withRequest(customRequest).build()
      assertEquals(customRequest, testMosaic.request)
    }

  @Test
  fun `registers tiles that are not mocked`() =
    runTest {
      val testMosaic = builder.build()
      assertIs<TestSingleTile>(testMosaic.getTile(TestSingleTile::class))
      assertIs<TestMultiTile>(testMosaic.getTile(TestMultiTile::class))
    }
}
