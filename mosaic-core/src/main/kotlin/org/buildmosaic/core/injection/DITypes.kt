package org.buildmosaic.core.injection

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Base interface for all dependency injection types in Mosaic.
 */
sealed interface MosaicDI

/**
 * Represents a dependency that hasn't been initialized yet.
 *
 * Stubs are used during the canvas building phase to defer dependency creation
 * until the canvas is fully constructed and ready to resolve dependencies.
 */
sealed interface Stub<T : Any> : MosaicDI {
  /**
   * Creates the dependency instance using the provided canvas factory.
   */
  suspend fun create(canvas: CanvasFactory): T

  /**
   * Converts this stub to a provider that can be used for synchronous retrieval.
   */
  suspend fun toProvider(canvas: CanvasFactory): Provider<T>
}

/**
 * A stub for singleton dependencies that ensures thread-safe lazy initialization.
 *
 * @param ctor The constructor function that creates the dependency instance
 */
class SingleStub<T : Any>(private val ctor: suspend CanvasFactory.() -> T) : Stub<T> {
  private val initLock = Mutex()

  @Volatile private lateinit var instance: T

  @Volatile private var isInitializing = false

  override suspend fun create(canvas: CanvasFactory): T {
    if (::instance.isInitialized) return instance
    check(!isInitializing) { "Circular dependency detected during initialization" }
    return initLock.withLock {
      if (::instance.isInitialized) return instance
      isInitializing = true
      try {
        instance = ctor(canvas)
        instance
      } finally {
        isInitializing = false
      }
    }
  }

  override suspend fun toProvider(canvas: CanvasFactory): Single<T> = Single(create(canvas))
}

/**
 * Represents a fully initialized dependency that can be retrieved synchronously.
 */
sealed interface Provider<T : Any> : MosaicDI {
  /**
   * Gets the dependency instance.
   */
  fun get(): T
}

/**
 * A provider for singleton dependencies.
 *
 * @param instance The singleton instance to provide
 */
class Single<T : Any>(internal val instance: T) : Provider<T> {
  override fun get() = instance
}
