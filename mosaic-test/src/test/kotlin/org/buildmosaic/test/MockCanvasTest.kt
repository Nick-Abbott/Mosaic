/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.buildmosaic.test

import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.injection.CanvasKey
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class MockCanvasTest {
  private val canvas = MockCanvas()

  @Test
  fun `register and retrieve without qualifier`() {
    val service = TestService("test")
    canvas.register(TestService::class, service)

    val retrieved = canvas.source(TestService::class)
    assertSame(service, retrieved)
  }

  @Test
  fun `register and retrieve with qualifier`() {
    val primaryService = TestService("primary")
    val secondaryService = TestService("secondary")

    canvas.register(TestService::class, "primary", primaryService)
    canvas.register(TestService::class, "secondary", secondaryService)

    val retrievedPrimary = canvas.source(TestService::class, "primary")
    val retrievedSecondary = canvas.source(TestService::class, "secondary")

    assertSame(primaryService, retrievedPrimary)
    assertSame(secondaryService, retrievedSecondary)
  }

  @Test
  fun `register and retrieve using CanvasKey`() {
    val service = TestService("keyed")
    val key = CanvasKey(TestService::class, "keyed")

    canvas.register(key, service)

    val retrieved = canvas.source(key)
    assertSame(service, retrieved)
  }

  @Test
  fun `sourceOr returns null for missing key`() {
    val key = CanvasKey(TestService::class, "missing")
    val result = canvas.sourceOr(key)
    assertNull(result)
  }

  @Test
  fun `sourceOr returns instance for existing key`() {
    val service = TestService("existing")
    val key = CanvasKey(TestService::class, "existing")
    canvas.register(key, service)

    val result = canvas.sourceOr(key)
    assertSame(service, result)
  }

  @Test
  fun `withLayer creates layered canvas`() =
    runTest {
      val baseService = TestService("base")
      canvas.register(TestService::class, baseService)

      val layeredCanvas =
        canvas.withLayer {
          // Layer configuration would go here
        }

      // Verify the layered canvas is created
      assertNotNull(layeredCanvas)
    }

  private data class TestService(val name: String)
}
