package org.buildmosaic.core.observation

/** No application work or scheduling belongs here. */
internal class ObservationCalls(private val observer: ExecutionObserver) {
  fun capture(): CallerSnapshot {
    val identity = ObservedExecution.current()?.takeIf { it.belongsTo(this) }?.identity
    return CallerSnapshot(identity, guarded(CallbackOperation.CAPTURE) { observer.captureCaller() })
  }

  fun sharesObserver(other: ObservationCalls): Boolean = observer === other.observer

  /** Publish origin resolution only after the guarded start callback has returned. */
  fun start(
    start: ExecutionStart?,
    producers: List<ProducerPublication>,
  ): ObservedExecution? {
    // A successful null opts out; an absent Result means the guarded start callback failed.
    val attempt = start?.let { guarded(CallbackOperation.START) { Result.success(observer.onStart(it)) } }
    val started = attempt?.getOrNull()
    val execution = started?.let { ObservedExecution(this, it) }
    producers.forEach { if (attempt == null) it.startFailed() else it.publish(started?.identity) }
    return execution
  }

  /** Arbitrary provider failures are isolated and reported without exception contents or recursion. */
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
