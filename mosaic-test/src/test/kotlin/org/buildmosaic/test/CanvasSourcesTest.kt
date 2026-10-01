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
import kotlin.test.assertNull
import kotlin.test.assertSame

class CanvasSourcesTest {
  private val sources = CanvasSources()

  @Test
  fun `register and retrieve without qualifier`() {
    val service = TestService("test")
    sources.register(TestService::class, service)

    val retrieved = sources.build().source(TestService::class)
    assertSame(service, retrieved)
  }

  @Test
  fun `register and retrieve with qualifier`() {
    val primaryService = TestService("primary")
    val secondaryService = TestService("secondary")

    sources.register(TestService::class, "primary", primaryService)
    sources.register(TestService::class, "secondary", secondaryService)

    val retrievedPrimary = sources.build().source(TestService::class, "primary")
    val retrievedSecondary = sources.build().source(TestService::class, "secondary")

    assertSame(primaryService, retrievedPrimary)
    assertSame(secondaryService, retrievedSecondary)
  }

  @Test
  fun `register and retrieve using CanvasKey`() {
    val service = TestService("keyed")
    val key = CanvasKey(TestService::class, "keyed")

    sources.register(key, service)

    val retrieved = sources.build().source(key)
    assertSame(service, retrieved)
  }

  @Test
  fun `sourceOr returns null for missing key`() {
    val key = CanvasKey(TestService::class, "missing")
    val result = sources.build().sourceOr(key)
    assertNull(result)
  }

  @Test
  fun `sourceOr returns instance for existing key`() {
    val service = TestService("existing")
    val key = CanvasKey(TestService::class, "existing")
    sources.register(key, service)

    val result = sources.build().sourceOr(key)
    assertSame(service, result)
  }

  @Test
  fun `withLayer creates layered canvas`() =
    runTest {
      val baseService = TestService("base")
      sources.register(TestService::class, baseService)

      val layeredCanvas =
        sources.build().withLayer {
          // Layer configuration would go here
        }

      assertSame(baseService, layeredCanvas.source(TestService::class))
    }

  @Test
  fun `build snapshots replaced sources`() {
    val first = TestService("first")
    val replacement = TestService("replacement")
    sources.register(TestService::class, first)
    sources.register(TestService::class, replacement)
    val built = sources.build()
    sources.register(TestService::class, TestService("later"))
    assertSame(replacement, built.source(TestService::class))
  }

  private data class TestService(val name: String)
}
