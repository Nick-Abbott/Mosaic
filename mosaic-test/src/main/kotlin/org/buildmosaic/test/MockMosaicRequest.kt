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

import org.buildmosaic.core.MosaicRequest

/**
 * A test implementation of [MosaicRequest] for use in unit and integration tests.
 *
 * This class provides a simple [MosaicRequest] implementation that can be used when testing
 * [Tile] implementations that don't require specific request data. For more complex test
 * scenarios, you can extend this class or implement [MosaicRequest] directly.
 *
 * ### Example Usage
 * ```kotlin
 * // Create a test request
 * val request = MockMosaicRequest()
 *
 * // Use in a test
 * val testMosaic = TestMosaicBuilder()
 *   .withRequest(request)
 *   .build()
 * ```
 *
 * @see MosaicRequest
 * @see TestMosaicBuilder
 */
class MockMosaicRequest : MosaicRequest
