package org.buildmosaic.core.injection

import org.buildmosaic.core.exception.MosaicMissingKeyException

private fun missingKeyError(key: CanvasKey<*>): Nothing = throw MosaicMissingKeyException(key)

/**
 * Builder for constructing a [Canvas] with dependency bindings.
 *
 * Use this builder to register dependencies that will be available for injection
 * in tiles and other canvas-aware components.
 */
class CanvasBuilder internal constructor() {
  @PublishedApi internal val bindings = mutableMapOf<CanvasKey<*>, Stub<*>>()

  /**
   * Registers a singleton dependency in the canvas.
   *
   * @param T The type of the dependency
   * @param key The [CanvasKey] associated with your dependency
   * @param ctor Constructor function that creates the dependency instance
   */
  fun <T : Any> single(
    key: CanvasKey<T>,
    ctor: suspend CanvasFactory.() -> T,
  ) = check(bindings.put(key, SingleStub(ctor)) == null) { "Duplicate binding for $key" }

  /**
   * Registers a singleton dependency in the canvas.
   *
   * @param T The type of the dependency
   * @param qualifier Optional qualifier to distinguish between multiple instances of the same type
   * @param ctor Constructor function that creates the dependency instance
   */
  inline fun <reified T : Any> single(
    qualifier: String? = null,
    noinline ctor: suspend CanvasFactory.() -> T,
  ) = single(CanvasKey(T::class, qualifier), ctor)
}

/**
 * Factory for creating [MosaicCanvas] instances from dependency bindings.
 *
 * This class handles the initialization of all registered dependencies and manages
 * their lifecycle, including automatic cleanup of [AutoCloseable] instances.
 *
 * @param bindings Map of dependency keys to their stub implementations
 * @param parent Optional parent canvas for dependency resolution fallback
 */
class CanvasFactory internal constructor(
  private val bindings: Map<CanvasKey<*>, Stub<*>>,
  private val parent: Canvas? = null,
) {
  private val closeables = mutableListOf<AutoCloseable>()

  /**
   * Builds the final [MosaicCanvas] by initializing all registered dependencies.
   */
  internal suspend fun build(): MosaicCanvas {
    val providers =
      bindings.mapValues { (_, binding) ->
        val provider = binding.toProvider(this)
        val instance = provider.get()
        if (instance is AutoCloseable) closeables.add(instance)
        provider
      }
    return MosaicCanvas(providers, closeables.toList(), parent)
  }

  /**
   * Creates a dependency instance during the canvas building phase.
   *
   * @param T The type of the dependency
   * @param key The canvas key identifying the dependency
   * @return The created dependency instance
   */
  suspend fun <T : Any> paint(key: CanvasKey<T>): T {
    @Suppress("UNCHECKED_CAST")
    val stub = bindings[key] as Stub<T>? ?: missingKeyError(key)
    return stub.create(this)
  }

  /**
   * Creates a dependency instance using reified type parameters.
   *
   * @param T The type of the dependency
   * @param qualifier Optional qualifier to distinguish between multiple instances
   * @return The created dependency instance
   */
  suspend inline fun <reified T : Any> paint(qualifier: String? = null): T = paint(CanvasKey(T::class, qualifier))
}

/**
 * Production implementation of [Canvas] that provides dependency injection capabilities.
 *
 * This canvas implementation supports hierarchical dependency resolution through parent canvases
 * and automatic lifecycle management of [AutoCloseable] dependencies.
 *
 * @param providers Map of initialized dependency providers
 * @param closeables List of closeable dependencies for cleanup
 * @param parent Optional parent canvas for fallback dependency resolution
 */
class MosaicCanvas internal constructor(
  private val providers: Map<CanvasKey<*>, Provider<*>>,
  private val closeables: List<AutoCloseable>,
  private val parent: Canvas? = null,
) : Canvas, AutoCloseable {
  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> sourceOr(key: CanvasKey<T>): T? {
    if (key !in providers) return parent?.sourceOr(key)
    return when (val provider = providers[key]!!) {
      is Single -> provider.get() as T
    }
  }

  override fun close() {
    closeables.forEach { closeable ->
      runCatching { closeable.close() }
        .onFailure { e -> System.err.println("Close hook failed: ${e.message}") }
    }
  }
}

/**
 * Creates a new [MosaicCanvas] using the canvas DSL.
 *
 * This is the primary way to create a canvas with dependency bindings. The canvas
 * supports hierarchical dependency resolution and automatic lifecycle management.
 *
 * @param parent Optional parent canvas for fallback dependency resolution
 * @param build DSL block for configuring dependency bindings
 * @return A fully initialized [MosaicCanvas]
 *
 * ```kotlin
 * val canvas = canvas {
 *   single<UserService> { UserServiceImpl() }
 *   single<DatabaseConfig> { loadConfig() }
 * }
 * ```
 */
suspend fun canvas(
  parent: Canvas? = null,
  build: CanvasBuilder.() -> Unit,
): MosaicCanvas = CanvasFactory(CanvasBuilder().apply(build).bindings, parent).build()
