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

import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.MultiTile
import org.buildmosaic.core.SingleTile

/**
 * Sample custom exception for testing.
 */
class TestException : Exception("My Test exception")

/**
 * Sample SingleTile implementation for testing.
 */
class TestSingleTile(mosaic: Mosaic) : SingleTile<String>(mosaic) {
  override suspend fun retrieve(): String {
    return "test-data"
  }
}

/**
 * Sample SingleTile that throws an exception for testing.
 */
class TestErrorSingleTile(mosaic: Mosaic) : SingleTile<String>(mosaic) {
  override suspend fun retrieve(): String {
    throw TestException()
  }
}

/**
 * Sample MultiTile implementation for testing.
 */
class TestMultiTile(mosaic: Mosaic) : MultiTile<String, Map<String, String>>(mosaic) {
  override suspend fun retrieveForKeys(keys: List<String>): Map<String, String> {
    return keys.associateWith { "data-for-$it" }
  }

  override fun normalize(
    key: String,
    response: Map<String, String>,
  ): String {
    return response[key] ?: "default"
  }
}

/**
 * Sample MultiTile that throws an exception for testing.
 */
class TestErrorMultiTile(mosaic: Mosaic) : MultiTile<String, Map<String, String>>(mosaic) {
  override suspend fun retrieveForKeys(keys: List<String>): Map<String, String> {
    throw TestException()
  }

  override fun normalize(
    key: String,
    response: Map<String, String>,
  ): String {
    return response[key] ?: "default"
  }
}

/**
 * Sample data classes for testing.
 */
data class TestUser(val id: String, val name: String)

data class TestProduct(val id: String, val name: String)

data class TestOrder(val id: String, val userId: String)

/**
 * Sample SingleTile with custom data type.
 */
class TestUserTile(mosaic: Mosaic) : SingleTile<TestUser>(mosaic) {
  override suspend fun retrieve(): TestUser {
    return TestUser("user1", "John Doe")
  }
}

/**
 * Sample MultiTile with custom data type.
 */
class TestProductTile(mosaic: Mosaic) : MultiTile<TestProduct, Map<String, TestProduct>>(mosaic) {
  override suspend fun retrieveForKeys(keys: List<String>): Map<String, TestProduct> {
    return keys.associateWith { TestProduct(it, "Product $it") }
  }

  override fun normalize(
    key: String,
    response: Map<String, TestProduct>,
  ): TestProduct {
    return response[key] ?: TestProduct(key, "Default Product")
  }
}
