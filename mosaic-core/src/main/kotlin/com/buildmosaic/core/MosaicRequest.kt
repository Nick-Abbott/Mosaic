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

package com.buildmosaic.core

/**
 * Interface for request-specific data that tiles can access.
 * This allows tiles to access request context, headers, parameters, etc.
 *
 * Extend this interface to add request-specific methods and properties.
 * Examples:
 * - headers: Map<String, String>
 * - parameters: Map<String, String>
 * - context: Any
 * - userId: String?
 * - etc.
 */
interface MosaicRequest
