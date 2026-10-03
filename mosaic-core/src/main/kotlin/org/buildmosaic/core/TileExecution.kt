package org.buildmosaic.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.buildmosaic.core.observation.ObservedExecution
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The structured scope of one admitted Tile invocation, with optional observation.
 * Results may publish inside the body; its attached children still own context and determine final
 * completion. This boundary preserves original body failures across coroutine stack recovery, settles
 * failed application work, and finalizes observation only after the scope has exited.
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
    withContext(context) {
      try {
        currentCoroutineContext().ensureActive()
        applicationFailure = block()
      } catch (failure: Throwable) {
        applicationFailure = failure
        fail(failure)
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
