package org.buildmosaic.core.injection

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Memoizes one binding while the eager dependency graph is being constructed. */
internal class SingleBinding<T : Any>(private val ctor: suspend CanvasFactory.() -> T) {
  private val initLock = Mutex()

  @Volatile private lateinit var instance: T

  @Suppress("TooGenericExceptionCaught")
  suspend fun create(canvas: CanvasFactory): T {
    if (::instance.isInitialized) return instance
    val path = currentCoroutineContext()[ConstructionPath]
    check(path?.contains(this) != true) { "Circular dependency detected during initialization" }
    return initLock.withLock {
      if (::instance.isInitialized) return instance
      var constructorFailure: Throwable? = null
      try {
        withContext(ConstructionPath(this@SingleBinding, path)) {
          try {
            // Record ownership before a cancelled context can discard the returned value.
            ctor(canvas).also {
              canvas.created(it)
              instance = it
            }
          } catch (failure: Throwable) {
            constructorFailure = failure
            throw failure
          }
        }
      } catch (failure: Throwable) {
        // Coroutine stack recovery may copy the exception while leaving the construction context.
        throw constructorFailure ?: failure
      }
    }
  }
}

/** Recursive ancestry is inherited by child coroutines; concurrent siblings have independent paths. */
private class ConstructionPath(
  private val binding: SingleBinding<*>,
  private val parent: ConstructionPath?,
) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<ConstructionPath>

  fun contains(candidate: SingleBinding<*>): Boolean = binding === candidate || parent?.contains(candidate) == true
}
