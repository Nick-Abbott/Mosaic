package org.buildmosaic.core.injection

import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.MosaicImpl
import org.buildmosaic.core.exception.MosaicMissingKeyException
import kotlin.reflect.KClass

/**
 * The key used to retrieve sources from the canvas
 *
 * @param type the KClass of the value stored
 * @param qualifier an optional name to qualify common types
 */
data class CanvasKey<T : Any>(val type: KClass<T>, val qualifier: String? = null) {
  override fun toString(): String =
    buildString {
      append(type.qualifiedName ?: "anonymous")
      qualifier?.let { append('[').append(it).append(']') }
    }
}

/**
 * Interface for dependency injection in Mosaic.
 *
 * Provides a mechanism to retrieve dependencies by their class type.
 * This is used internally by the [Mosaic] class to support dependency injection
 * in DSL tile functions.
 */
interface Canvas {
  /**
   * Retrieves an instance of the registered object of the specified type and qualifier
   *
   * @param T The type of the object to retrieve
   * @param type The [KClass] of the object
   * @return The registered object
   * @throws [MosaicMissingKeyException] if no instance is registered for the type
   */
  fun <T : Any> source(
    type: KClass<T>,
    qualifier: String? = null,
  ): T = source(CanvasKey(type, qualifier))

  /**
   * Retrieves an instance of the registered object under the [CanvasKey]
   *
   * @param T the type of the registered object
   * @param key the key the object is registered under
   * @return The registered object
   * @throws [MosaicMissingKeyException] if no instance is registered for the type
   */
  fun <T : Any> source(key: CanvasKey<T>): T = sourceOr(key) ?: throw MosaicMissingKeyException(key)

  /**
   * Retrieves an instance of the registered object
   * Returns null if the object isn't found
   *
   * @param T the type of the registered object
   * @param type The [KClass] of the object
   * @param qualifier an optional qualifier for the type
   * @return The registered object
   */
  fun <T : Any> sourceOr(
    type: KClass<T>,
    qualifier: String? = null,
  ): T? = sourceOr(CanvasKey(type, qualifier))

  /**
   * Retrieves an instance of the registered object under the [CanvasKey]
   * Returns null if the object isn't found
   *
   * @param T the type of the registered object
   * @param key the key the object is registered under
   * @return The registered object
   */
  fun <T : Any> sourceOr(key: CanvasKey<T>): T?

  /**
   * A DSL method to create another layer on your [Canvas]
   * The returned object will be a new [Canvas] depending on the sources of the parent
   * This does not modify the parent in any way
   *
   * @param build A block of code registering all sources for your [Canvas] layer
   */
  suspend fun withLayer(build: CanvasBuilder.() -> Unit): MosaicCanvas = canvas(this, build)
}

/**
 * Inline extension function to retrieve a dependency using reified type parameters.
 *
 * @param T The type of the dependency to retrieve
 * @return An instance of the requested type
 * @throws [MosaicMissingKeyException] if no instance is registered for the type
 */
inline fun <reified T : Any> Canvas.source(): T = source(T::class)

/**
 * Inline extension function to retrieve a dependency using reified type parameters.
 * Returns null if the source is not found.
 *
 * @param T The type of the dependency to retrieve
 * @return An instance of the requested type
 */
inline fun <reified T : Any> Canvas.sourceOr(): T? = sourceOr(T::class)

/**
 * Creates a new [Mosaic] instance
 *
 * @return An instance of [Mosaic] scoped to the [Canvas]
 */
fun Canvas.create(): Mosaic = MosaicImpl(this)
