package org.buildmosaic.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import org.buildmosaic.core.observation.ObservedExecution
import org.buildmosaic.core.observation.ProducerPublication

/** A request-owned cached result and origin; publication never borrows the publishing execution. */
internal class CacheEntry<V>(
  requestJob: Job,
  admissionMonitor: Any,
  val producer: ProducerPublication?,
) {
  val result = CompletableDeferred<V>(requestJob)

  init {
    if (producer != null) {
      result.invokeOnCompletion {
        if (!requestJob.isActive) {
          // Coordinate abandonment with execution admission; notify subscribers outside both locks.
          val notification = synchronized(admissionMonitor) { producer.prepareAbandon() }
          notification?.invoke()
        }
      }
    }
  }

  fun publish(value: V) = ObservedExecution.withoutCaller { result.complete(value) }

  fun fail(failure: Throwable) = ObservedExecution.withoutCaller { result.completeExceptionally(failure) }
}
