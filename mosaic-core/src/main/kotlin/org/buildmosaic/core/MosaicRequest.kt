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

package org.buildmosaic.core

/**
 * Interface representing request-specific data that can be accessed by [Tile] implementations.
 *
 * [MosaicRequest] serves as a container for request-scoped data such as:
 * - HTTP headers and parameters
 * - Authentication/authorization context
 * - Request metadata
 * - User session information
 *
 * Implement this interface to provide request context to your tiles. Common implementations might include:
 *
 * ```kotlin
 * class HttpMosaicRequest(
 *   val headers: Map<String, String>,
 *   val parameters: Map<String, String>,
 *   val userId: String?,
 *   val session: HttpSession
 * ) : MosaicRequest
 * ```
 *
 * @see Mosaic The main entry point that provides access to the current request
 * @see Tile The interface for tiles that can access request data
 */
interface MosaicRequest
