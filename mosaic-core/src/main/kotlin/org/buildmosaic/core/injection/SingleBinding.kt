package org.buildmosaic.core.injection

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Memoizes one binding while the eager dependency graph is being constructed. */
internal class SingleBinding<T : Any>(private val ctor: suspend CanvasFactory.() -> T) {
  private val initLock = Mutex()

  @Volatile private lateinit var instance: T

  @Volatile private var isInitializing = false

  suspend fun create(canvas: CanvasFactory): T {
    if (::instance.isInitialized) return instance
    check(!isInitializing) { "Circular dependency detected during initialization" }
    return initLock.withLock {
      if (::instance.isInitialized) return instance
      isInitializing = true
      try {
        instance = ctor(canvas).also { canvas.created(it) }
        instance
      } finally {
        isInitializing = false
      }
    }
  }
}
