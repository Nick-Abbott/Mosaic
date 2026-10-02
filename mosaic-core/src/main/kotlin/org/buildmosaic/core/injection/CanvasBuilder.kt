package org.buildmosaic.core.injection

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.buildmosaic.core.exception.MosaicMissingKeyException
import java.util.IdentityHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

private fun missingKeyError(key: CanvasKey<*>): Nothing = throw MosaicMissingKeyException(key)

/**
 * Builder for constructing a [Canvas] with dependency bindings.
 *
 * Use this builder to register dependencies that will be available for injection
 * in tiles and other canvas-aware components.
 */
class CanvasBuilder internal constructor() {
  internal val bindings = mutableMapOf<CanvasKey<*>, SingleBinding<*>>()

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
 * @param bindings Map of dependency keys to their construction state
 * @param parent Optional parent canvas for dependency resolution fallback
 */
class CanvasFactory internal constructor(
  private val bindings: Map<CanvasKey<*>, SingleBinding<*>>,
  private val parent: Canvas? = null,
) {
  private val closeables = mutableListOf<AutoCloseable>()
  private val dependencies = IdentityHashMap<SingleBinding<*>, MutableList<SingleBinding<*>>>()
  private val constructionKey = object : CoroutineContext.Key<ConstructingBinding> {}

  internal fun constructionContext(binding: SingleBinding<*>): CoroutineContext =
    ConstructingBinding(binding, constructionKey)

  internal fun created(instance: Any) {
    if (instance is AutoCloseable) synchronized(closeables) { closeables.add(instance) }
  }

  private fun createdResources(): List<AutoCloseable> = synchronized(closeables) { closeables.toList() }

  /** Tracks outstanding local resolutions, including requests from concurrent child constructors. */
  private suspend fun <T : Any> create(binding: SingleBinding<T>): T {
    val owner = currentCoroutineContext()[constructionKey]?.binding
    synchronized(dependencies) {
      binding.instanceOrNull()?.let { return it }
      if (owner != null) {
        check(!dependsOn(binding, owner)) { "Circular dependency detected during initialization" }
        // Keep duplicate requests until every caller has finished waiting.
        dependencies.getOrPut(owner) { mutableListOf() }.add(binding)
      }
    }
    try {
      return binding.create(this)
    } finally {
      if (owner != null) {
        synchronized(dependencies) {
          val requests = dependencies.getValue(owner)
          requests.remove(binding)
          if (requests.isEmpty()) dependencies.remove(owner)
        }
      }
    }
  }

  private fun dependsOn(
    binding: SingleBinding<*>,
    target: SingleBinding<*>,
    visited: MutableSet<SingleBinding<*>> = mutableSetOf(),
  ): Boolean {
    if (binding === target) return true
    if (!visited.add(binding)) return false
    return dependencies[binding].orEmpty().any { dependsOn(it, target, visited) }
  }

  /** Resolves all bindings, transferring local resource ownership only after successful construction. */
  @Suppress("TooGenericExceptionCaught")
  internal suspend fun build(): Canvas {
    try {
      currentCoroutineContext().ensureActive()
      val instances = bindings.mapValues { (_, binding) -> create(binding) }
      currentCoroutineContext().ensureActive()
      return Canvas(instances, createdResources(), parent)
    } catch (failure: Throwable) {
      createdResources().asReversed().forEach { resource ->
        runCatching { resource.close() }.onFailure { cleanupFailure ->
          if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
        }
      }
      throw failure
    }
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
    if (local != null) return create(local)
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

/** Child coroutines inherit the current constructor for each factory, including across nested Canvas builds. */
private class ConstructingBinding(
  val binding: SingleBinding<*>,
  key: CoroutineContext.Key<ConstructingBinding>,
) : AbstractCoroutineContextElement(key)

/**
 * Creates a new [Canvas] using the canvas DSL.
 *
 * This is the primary way to create a canvas with dependency bindings. The canvas
 * supports hierarchical dependency resolution and automatic lifecycle management.
 *
 * @param parent Optional parent canvas for fallback dependency resolution
 * @param build DSL block for configuring dependency bindings
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
): Canvas = CanvasFactory(CanvasBuilder().apply(build).bindings, parent).build()
