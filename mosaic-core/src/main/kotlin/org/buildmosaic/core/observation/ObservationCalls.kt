package org.buildmosaic.core.observation

/** No application work or scheduling belongs here. */
internal class ObservationCalls(private val observer: ExecutionObserver) {
  fun capture(): CallerSnapshot {
    val identity = ObservedExecution.current()?.identity
    return CallerSnapshot(identity, guarded(CallbackOperation.CAPTURE) { observer.captureCaller() })
  }

  // A successful null opts out; an absent Result means the guarded start callback failed.
  fun start(start: ExecutionStart): Result<StartedObservation?>? =
    guarded(CallbackOperation.START) { Result.success(observer.onStart(start)) }

  @Suppress("TooGenericExceptionCaught")
  fun <T> guarded(
    operation: CallbackOperation,
    block: () -> T,
  ): T? =
    ObservedExecution.withoutCaller {
      try {
        block()
      } catch (failure: Throwable) {
        try {
          observer.onCallbackFailure(CallbackFailure(operation, failure.javaClass.name))
        } catch (_: Throwable) {
          // Diagnostics must never recurse or affect application work.
        }
        null
      }
    }
}
