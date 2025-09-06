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

/**
 * Defines the possible behaviors for mock tiles in test scenarios.
 *
 * This enum is used by [TestMosaicBuilder] to configure how mock tiles should behave during testing.
 */
internal enum class MockBehavior {
  /**
   * The mock will return the specified data successfully.
   *
   * Example:
   * ```kotlin
   * TestMosaicBuilder()
   *   .withMockTile<MyTile>(MockBehavior.SUCCESS) { "test data" }
   *   .build()
   * ```
   */
  SUCCESS,

  /**
   * The mock will throw the provided [Throwable] when called.
   *
   * Example:
   * ```kotlin
   * TestMosaicBuilder()
   *   .withMockTile<MyTile>(MockBehavior.ERROR) { RuntimeException("Test error") }
   *   .build()
   * ```
   */
  ERROR,

  /**
   * The mock will delay for a configured duration before returning data.
   *
   * Example:
   * ```kotlin
   * TestMosaicBuilder()
   *   .withMockTile<MyTile>(MockBehavior.DELAY) {
   *     delay(100) // 100ms delay
   *     "delayed data"
   *   }
   *   .build()
   * ```
   */
  DELAY,

  /**
   * The mock will execute the provided custom lambda when called.
   *
   * This allows for complex mock behavior that doesn't fit the other categories.
   *
   * Example:
   * ```kotlin
   * TestMosaicBuilder()
   *   .withMockTile<MyTile>(MockBehavior.CUSTOM) {
   *     // Custom logic here
   *     if (condition) "result1" else "result2"
   *   }
   *   .build()
   * ```
   */
  CUSTOM,
}
