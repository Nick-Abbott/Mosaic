@file:OptIn(ExperimentalMosaicInstrumentation::class)

package org.buildmosaic.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import org.buildmosaic.core.instrumentation.ExecutionCompletion
import org.buildmosaic.core.instrumentation.ExecutionOutcome
import org.buildmosaic.core.instrumentation.ExperimentalMosaicInstrumentation
import org.buildmosaic.core.instrumentation.InstrumentationCalls
import org.buildmosaic.core.instrumentation.MosaicInstrumentation
import org.buildmosaic.core.instrumentation.ProducerReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal class ExecutionRunner(private val calls: InstrumentationCalls) {
  fun observe(execution: MosaicInstrumentation.Execution?): ExecutionOwner? {
    if (execution == null) return null
    val identity = calls.invoke { execution.identity }
    return ExecutionOwner(calls, execution, identity)
  }

  fun publish(
    producer: ProducerReference,
    owner: ExecutionOwner?,
  ) {
    val identity = owner?.identity
    if (identity == null) producer.abandon() else producer.publish(identity)
  }

  @Suppress("TooGenericExceptionCaught")
  suspend fun execute(
    owner: ExecutionOwner?,
    failValues: (Throwable) -> Unit,
    body: suspend (complete: (Throwable?) -> Unit) -> Unit,
  ) {
    var completion: ExecutionCompletion? = null
    try {
      val context = if (owner?.identity == null) EmptyCoroutineContext else calls.context(owner.execution) + owner
      withContext(context) {
        try {
          body { completion = completed(it) }
        } catch (failure: Throwable) {
          if (completion == null) completion = completed(failure)
          // Keep the original application exception in the cache, before withContext recovery.
          failValues(failure)
        } finally {
          owner?.active = false
        }
      }
    } catch (failure: Throwable) {
      if (completion == null) completion = completed(failure)
      failValues(failure)
    } finally {
      owner?.active = false
      if (owner != null) {
        val actual = checkNotNull(completion) { "Execution ended without completion" }
        calls.invoke { owner.execution.complete(actual) }
      }
    }
  }

  private fun completed(failure: Throwable?): ExecutionCompletion {
    val outcome =
      when (failure) {
        null -> ExecutionOutcome.SUCCESS
        is CancellationException -> ExecutionOutcome.CANCELLED
        else -> ExecutionOutcome.FAILURE
      }
    return ExecutionCompletion(outcome, failure, System.nanoTime())
  }
}

internal class ExecutionOwner(
  val calls: InstrumentationCalls,
  val execution: MosaicInstrumentation.Execution,
  val identity: MosaicInstrumentation.ExecutionIdentity?,
) : ThreadContextElement<ExecutionOwner?> {
  companion object Key : CoroutineContext.Key<ExecutionOwner> {
    val current = ThreadLocal<ExecutionOwner?>()
  }

  @Volatile var active = true
  override val key: CoroutineContext.Key<*> = Key

  override fun updateThreadContext(context: CoroutineContext): ExecutionOwner? =
    current.get().also { current.set(if (active) this else null) }

  override fun restoreThreadContext(
    context: CoroutineContext,
    oldState: ExecutionOwner?,
  ) {
    if (oldState == null) current.remove() else current.set(oldState)
  }
}
