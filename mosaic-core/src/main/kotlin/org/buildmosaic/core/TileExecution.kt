package org.buildmosaic.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.buildmosaic.core.observation.ObservedExecution
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Runs admitted Tile work in its Mosaic-owned launch, adding a scope to install or clear observation.
 * Observed completion includes attached children even when the body publishes its result earlier.
 * Without observation to install or clear, work runs directly in the launch. Tile bodies can use
 * coroutineScope for structured child work.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend inline fun executeTile(
  observation: ObservedExecution?,
  crossinline fail: (Throwable) -> Unit,
  crossinline block: suspend () -> Throwable?,
) {
  var applicationFailure: Throwable? = null
  val context =
    observation?.context
      ?: if (ObservedExecution.current() != null) ObservedExecution.unobservedContext else EmptyCoroutineContext
  try {
    if (observation == null && context === EmptyCoroutineContext) {
      currentCoroutineContext().ensureActive()
      applicationFailure = block()
    } else {
      withContext(context) {
        try {
          currentCoroutineContext().ensureActive()
          applicationFailure = block()
        } catch (failure: Throwable) {
          applicationFailure = failure
          fail(failure)
        }
      }
    }
    observation?.recordCompletion(applicationFailure)
  } catch (failure: Throwable) {
    // Keep original body failures; cancellation may instead be caused by a failing child.
    val original = applicationFailure?.takeUnless { it is CancellationException } ?: failure
    observation?.recordCompletion(original)
    fail(original)
  } finally {
    observation?.finish()
  }
}
