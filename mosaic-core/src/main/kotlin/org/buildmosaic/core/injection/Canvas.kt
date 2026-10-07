package org.buildmosaic.core.injection

import kotlinx.coroutines.currentCoroutineContext
import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.MosaicImpl
import org.buildmosaic.core.MosaicRuntimeConfig
import org.buildmosaic.core.exception.MosaicMissingKeyException
import org.buildmosaic.core.withMosaicExecution
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
 * Resolved dependencies and locally owned resources for Mosaic.
 *
 * Provides a mechanism to retrieve dependencies by their class type.
 * This is used internally by the [Mosaic] class to support dependency injection
 * in DSL tile functions. Construct a Canvas with [canvas] or [withLayer].
 */
class Canvas internal constructor(
  private val instances: Map<CanvasKey<*>, Any>,
  private val closeables: List<AutoCloseable>,
  private val parent: Canvas? = null,
  internal val runtimeConfig: MosaicRuntimeConfig = MosaicRuntimeConfig.EMPTY,
) : AutoCloseable {
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
  fun <T : Any> source(key: CanvasKey<T>): T = sourceOrNull(key) ?: throw MosaicMissingKeyException(key)

  /**
   * Retrieves an instance of the registered object
   * Returns null if the object isn't found
   *
   * @param T the type of the registered object
   * @param type The [KClass] of the object
   * @param qualifier an optional qualifier for the type
   * @return The registered object
   */
  fun <T : Any> sourceOrNull(
    type: KClass<T>,
    qualifier: String? = null,
  ): T? = sourceOrNull(CanvasKey(type, qualifier))

  /**
   * Retrieves an instance of the registered object under the [CanvasKey]
   * Returns null if the object isn't found
   *
   * @param T the type of the registered object
   * @param key the key the object is registered under
   * @return The registered object
   */
  @Suppress("UNCHECKED_CAST")
  fun <T : Any> sourceOrNull(key: CanvasKey<T>): T? = instances[key] as T? ?: parent?.sourceOrNull(key)

  /**
   * A DSL method to create another layer on your [Canvas]
   * The returned object will be a new [Canvas] depending on the sources of the parent
   * This does not modify the parent in any way. Child bindings are constructed eagerly;
   * a child constructor can use [CanvasFactory.source] to resolve local bindings first and
   * then fall back to the parent. Child overrides do not rewire services already created
   * by the parent.
   *
   * @param build A block of code registering all sources for your [Canvas] layer
   */
  suspend fun withLayer(build: CanvasBuilder.() -> Unit): Canvas = canvas(this, build)

  /**
   * Closes locally constructed [AutoCloseable] resources in reverse creation order,
   * continuing after failures. Parent resources and externally owned [CanvasBuilder.instance]
   * bindings are unaffected. Finish scoped Mosaic work before closing its Canvas.
   */
  override fun close() {
    closeables.asReversed().forEach { closeable ->
      runCatching { closeable.close() }
        .onFailure { failure -> System.err.println("Close hook failed: ${failure.message}") }
    }
  }
}

/**
 * Inline extension function to retrieve a dependency using reified type parameters.
 *
 * @param T The type of the dependency to retrieve
 * @param qualifier An optional name distinguishing bindings of the same type
 * @return An instance of the requested type
 * @throws [MosaicMissingKeyException] if no instance is registered for the type
 */
inline fun <reified T : Any> Canvas.source(qualifier: String? = null): T = source(T::class, qualifier)

/**
 * Inline extension function to retrieve a dependency using reified type parameters.
 * Returns null if the source is not found.
 *
 * @param T The type of the dependency to retrieve
 * @param qualifier An optional name distinguishing bindings of the same type
 * @return An instance of the requested type
 */
inline fun <reified T : Any> Canvas.sourceOrNull(qualifier: String? = null): T? = sourceOrNull(T::class, qualifier)

/**
 * Executes [block] with a request-owned [Mosaic], inheriting the calling coroutine's context.
 * Tile producers are supervised siblings: cancelling a waiter does not cancel shared work.
 * Request cancellation cancels unfinished producers. Every exit cancels speculative work and
 * waits for producer and attached-child cleanup before returning or propagating the block's failure.
 * Each invocation has a fresh cache. Keep the complete handler inside [block]; do not retain
 * its Mosaic for later requests. This function does not close this Canvas. Scope a child
 * Canvas separately when it constructs owned resources.
 *
 * ```kotlin
 * return canvas.withMosaic {
 *   handler(this)
 * }
 * ```
 */
suspend fun <R> Canvas.withMosaic(block: suspend Mosaic.() -> R): R =
  MosaicImpl(this, currentCoroutineContext()).withMosaicExecution(block)
