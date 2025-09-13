package org.buildmosaic.core.vtwo.injection

import org.buildmosaic.core.vtwo.Mosaic
import kotlin.reflect.KClass

/**
 * Interface for dependency injection in Mosaic.
 *
 * Provides a mechanism to retrieve dependencies by their class type.
 * This is used internally by the [Mosaic] class to support dependency injection
 * in DSL tile functions.
 */
interface Canvas {
  /**
   * Retrieves an instance of the specified type.
   *
   * @param T The type of the dependency to retrieve
   * @param type The class of the dependency
   * @return An instance of the requested type
   * @throws IllegalArgumentException if no instance is registered for the type
   */
  fun <T : Any> source(type: KClass<T>): T
}

/**
 * Inline extension function to retrieve a dependency using reified type parameters.
 *
 * @param T The type of the dependency to retrieve
 * @return An instance of the requested type
 */
inline fun <reified T : Any> Canvas.source(): T = source(T::class)

/**
 * Creates a new [Mosaic] to process the [Scene]
 *
 * @param scene The scene that you are processing
 * @return An instance of [Mosaic] scoped to the [Canvas] and [Scene]
 */
fun Canvas.create(scene: Scene): Mosaic = Mosaic(scene, this)
