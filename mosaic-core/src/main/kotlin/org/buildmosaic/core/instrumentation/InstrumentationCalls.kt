package org.buildmosaic.core.instrumentation

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Only provider code belongs inside invoke; runtime invariants stay outside this failure boundary. */
internal class InstrumentationCalls(val provider: MosaicInstrumentation) {
  @Suppress("TooGenericExceptionCaught")
  inline fun <T> invoke(callback: () -> T): T? =
    try {
      callback()
    } catch (failure: Throwable) {
      report(failure)
      null
    }

  @Suppress("TooGenericExceptionCaught", "SwallowedException")
  fun report(failure: Throwable) {
    try {
      provider.onCallbackFailure(failure)
    } catch (ignored: Throwable) {
      // A failed diagnostic must not escape into application execution either.
    }
  }

  fun context(execution: MosaicInstrumentation.Execution): CoroutineContext =
    invoke {
      execution.coroutineContext.fold<CoroutineContext>(EmptyCoroutineContext) { context, element ->
        require(element is ThreadContextElement<*>) { "Instrumentation context must contain only ThreadContextElement" }
        @Suppress("UNCHECKED_CAST")
        context + GuardedElement(element as ThreadContextElement<Any?>, this)
      }
    } ?: EmptyCoroutineContext
}

private class GuardedElement(
  private val delegate: ThreadContextElement<Any?>,
  private val calls: InstrumentationCalls,
) : ThreadContextElement<GuardedElement.State?> {
  // A private, distinct key also prevents an adapter from replacing Mosaic's ownership element.
  override val key = object : CoroutineContext.Key<GuardedElement> {}

  class State(val value: Any?)

  override fun updateThreadContext(context: CoroutineContext): State? =
    calls.invoke { State(delegate.updateThreadContext(context)) }

  override fun restoreThreadContext(
    context: CoroutineContext,
    oldState: State?,
  ) {
    if (oldState != null) calls.invoke { delegate.restoreThreadContext(context, oldState.value) }
  }
}
