package org.buildmosaic.core.observation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ThreadContextElement
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Lives only as long as an actual execution; cache entries retain identities through publications. */
internal class ObservedExecution(
  private val calls: ObservationCalls,
  private val observation: StartedObservation,
) {
  val identity: ExecutionIdentity? get() = observation.identity
  val context: CoroutineContext = ExecutionElement()

  @Volatile private var active = true
  private var completion: ExecutionCompletion? = null
  private val finished = AtomicBoolean()

  fun belongsTo(owner: ObservationCalls): Boolean = calls.sharesObserver(owner)

  fun dependency(producer: ProducerPublication) {
    if (active && producer.belongsTo(calls)) {
      calls.guarded(CallbackOperation.DEPENDENCY) { observation.callbacks.onDependency(producer) }
    }
  }

  fun recordCompletion(failure: Throwable?) {
    active = false
    if (completion == null) {
      val outcome =
        when (failure) {
          null -> ExecutionOutcome.SUCCESS
          is CancellationException -> ExecutionOutcome.CANCELLATION
          else -> ExecutionOutcome.FAILURE
        }
      completion = ExecutionCompletion(outcome, System.nanoTime(), failure?.javaClass?.name)
    }
  }

  fun finish() {
    val recorded = checkNotNull(completion) { "Execution completion must precede notification" }
    if (finished.compareAndSet(false, true)) {
      calls.guarded(CallbackOperation.COMPLETE) { observation.callbacks.onComplete(recorded) }
    }
  }

  private inner class ExecutionElement :
    ThreadContextElement<Installation>, AbstractCoroutineContextElement(Key) {
    override fun updateThreadContext(context: CoroutineContext): Installation {
      val previous = executing.get()
      val current = if (active) this@ObservedExecution else null
      executing.set(current)
      val token =
        current?.observation?.context?.let { installation ->
          calls.guarded(CallbackOperation.INSTALL) { installation.install() }
        }
      return Installation(previous, token)
    }

    override fun restoreThreadContext(
      context: CoroutineContext,
      oldState: Installation,
    ) {
      try {
        oldState.token?.let { token -> calls.guarded(CallbackOperation.RESTORE) { token.close() } }
      } finally {
        executing.set(oldState.previous)
      }
    }
  }

  private class Installation(val previous: ObservedExecution?, val token: AutoCloseable?)

  private object Key : CoroutineContext.Key<CoroutineContext.Element>

  private object UnobservedElement :
    ThreadContextElement<ObservedExecution?>, AbstractCoroutineContextElement(Key) {
    override fun updateThreadContext(context: CoroutineContext): ObservedExecution? =
      executing.get().also { executing.set(null) }

    override fun restoreThreadContext(
      context: CoroutineContext,
      oldState: ObservedExecution?,
    ) {
      executing.set(oldState)
    }
  }

  companion object {
    private val executing = ThreadLocal<ObservedExecution?>()

    // Inline unobserved execution must not borrow the surrounding observed execution's relationship.
    val unobservedContext: CoroutineContext get() = UnobservedElement

    fun current(): ObservedExecution? = executing.get()?.takeIf { it.active }

    fun <T> withoutCaller(block: () -> T): T {
      val previous = executing.get()
      executing.set(null)
      try {
        return block()
      } finally {
        executing.set(previous)
      }
    }
  }
}
