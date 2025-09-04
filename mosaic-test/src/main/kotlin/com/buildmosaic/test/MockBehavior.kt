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

/**
 * Internal behaviors used by [TestMosaicBuilder] when constructing mocks.
 */
internal enum class MockBehavior {
  /** Mock returns the specified data successfully */
  SUCCESS,

  /** Mock throws the provided [Throwable] */
  ERROR,

  /** Mock delays for a configured duration before returning data */
  DELAY,

  /** Mock executes the provided custom lambda */
  CUSTOM,
}
