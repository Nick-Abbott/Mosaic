package org.buildmosaic.core.vtwo.injection

import org.buildmosaic.core.vtwo.Mosaic
import org.buildmosaic.core.vtwo.MosaicImpl
import org.buildmosaic.core.vtwo.exception.MosaicMissingKeyException
import kotlin.reflect.KClass

/**
 * The key used to retrieve sources from the canvas
 *
 * @param type the KClass of the value stored
 * @param qualifier an optional name to qualify common types
 */
data class CanvasKey<T : Any>(val type: KClass<T>, val qualifier: String? = null) {
  override fun toString(): String = type.qualifiedName + (qualifier?.let { "[$it]" } ?: "")
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

  fun <T : Any> sourceOr(
    type: KClass<T>,
    qualifier: String? = null,
  ): T? = sourceOr(CanvasKey(type, qualifier))

  fun <T : Any> sourceOr(key: CanvasKey<T>): T?

  suspend fun withLayer(build: CanvasBuilder.() -> Unit): MosaicCanvas = canvas(this, build)
}

/**
 * Inline extension function to retrieve a dependency using reified type parameters.
 *
 * @param T The type of the dependency to retrieve
 * @return An instance of the requested type
 */
inline fun <reified T : Any> Canvas.source(): T = source(T::class)

inline fun <reified T : Any> Canvas.sourceOr(): T? = sourceOr(T::class)

/**
 * Creates a new [Mosaic] to process the [Scene]
 *
 * @param scene The scene that you are processing
 * @return An instance of [Mosaic] scoped to the [Canvas] and [Scene]
 */
fun Canvas.create(): Mosaic = MosaicImpl(this)
