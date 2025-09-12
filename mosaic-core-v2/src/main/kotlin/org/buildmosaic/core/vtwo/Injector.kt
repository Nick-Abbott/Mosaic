package org.buildmosaic.core.vtwo

import kotlin.reflect.KClass

/**
 * Interface for dependency injection in Mosaic V2.
 *
 * Provides a mechanism to retrieve dependencies by their class type.
 * This is used internally by the [Mosaic] class to support dependency injection
 * in DSL tile functions.
 */
interface Injector {
  /**
   * Retrieves an instance of the specified type.
   *
   * @param T The type of the dependency to retrieve
   * @param type The class of the dependency
   * @return An instance of the requested type
   * @throws IllegalArgumentException if no instance is registered for the type
   */
  fun <T : Any> get(type: KClass<T>): T
}

/**
 * Inline extension function to retrieve a dependency using reified type parameters.
 *
 * @param T The type of the dependency to retrieve
 * @return An instance of the requested type
 */
inline fun <reified T : Any> Injector.get(): T = get(T::class)
