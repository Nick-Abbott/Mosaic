package org.buildmosaic.core.injection

import org.buildmosaic.core.exception.MosaicMissingKeyException

private fun missingKeyError(key: CanvasKey<*>): Nothing = throw MosaicMissingKeyException(key)

/**
 * Builder for constructing a [Canvas] with dependency bindings and durable runtime configuration.
 *
 * Use this builder to register dependencies that will be available for injection
 * in tiles and other canvas-aware components.
 */
class CanvasBuilder internal constructor(
  private val inheritedRuntimeConfig: MosaicRuntimeConfig = MosaicRuntimeConfig.EMPTY,
) {
  @PublishedApi internal val bindings = mutableMapOf<CanvasKey<*>, SingleBinding<*>>()
  private var runtimeConfigBuilder: MosaicRuntimeConfig.Builder? = null

  internal fun configureRuntime(): MosaicRuntimeConfig.Builder =
    runtimeConfigBuilder ?: MosaicRuntimeConfig.Builder(inheritedRuntimeConfig).also { runtimeConfigBuilder = it }

  internal fun runtimeConfig(): MosaicRuntimeConfig = runtimeConfigBuilder?.build() ?: inheritedRuntimeConfig

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
  ) = check(bindings.put(key, SingleBinding(ctor)) == null) { "Duplicate binding for $key" }

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
 * Factory for creating [Canvas] instances from dependency bindings.
 *
 * This class handles the initialization of all registered dependencies and manages
 * their lifecycle, including cleanup of locally owned [AutoCloseable] instances when the canvas is closed.
 *
 * @param bindings Map of dependency keys to their construction-time bindings
 * @param parent Optional parent canvas for dependency resolution fallback
 */
class CanvasFactory internal constructor(
  private val bindings: Map<CanvasKey<*>, SingleBinding<*>>,
  private val parent: Canvas? = null,
) {
  private val closeables = mutableListOf<AutoCloseable>()

  internal fun created(instance: Any) {
    if (instance is AutoCloseable) synchronized(closeables) { closeables.add(instance) }
  }

  /** Builds eagerly; failure closes successfully constructed local values in reverse creation order. */
  @Suppress("TooGenericExceptionCaught")
  internal suspend fun build(runtimeConfig: MosaicRuntimeConfig = MosaicRuntimeConfig.EMPTY): Canvas =
    try {
      val instances = bindings.mapValues { (_, binding) -> binding.create(this) }
      Canvas(instances, instances.values.filterIsInstance<AutoCloseable>(), parent, runtimeConfig)
    } catch (failure: Throwable) {
      val owned = synchronized(closeables) { closeables.toList() }
      owned.asReversed().forEach { closeable ->
        runCatching { closeable.close() }.onFailure { cleanup ->
          if (cleanup !== failure) failure.addSuppressed(cleanup)
        }
      }
      throw failure
    }

  /**
   * Creates a dependency instance during the canvas building phase.
   * Local bindings are resolved first. If this canvas does not contain [key], lookup falls
   * back through the parent canvas. Construction is eager, so parent-owned instances are
   * reused and child overrides do not rewire services already created by the parent.
   *
   * @param T The type of the dependency
   * @param key The canvas key identifying the dependency
   * @return The created dependency instance
   */
  suspend fun <T : Any> paint(key: CanvasKey<T>): T {
    @Suppress("UNCHECKED_CAST")
    val local = bindings[key] as SingleBinding<T>?
    if (local != null) return local.create(this)
    return parent?.sourceOr(key) ?: missingKeyError(key)
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
 * Creates a new [Canvas] using the canvas DSL.
 *
 * This is the primary way to create a canvas with dependency bindings. The canvas
 * supports hierarchical dependency resolution, inherited runtime configuration, and automatic lifecycle management.
 *
 * @param parent Optional parent canvas for fallback dependency resolution
 * @param build DSL block for configuring dependency bindings and runtime settings
 * @return A fully initialized [Canvas]
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
): Canvas {
  val inheritedConfig = parent?.runtimeConfig ?: MosaicRuntimeConfig.EMPTY
  val builder = CanvasBuilder(inheritedConfig).apply(build)
  return CanvasFactory(builder.bindings, parent).build(builder.runtimeConfig())
}
