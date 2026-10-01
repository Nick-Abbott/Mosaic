package org.buildmosaic.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ThreadContextElement
import org.buildmosaic.core.instrumentation.ExecutionCompletion
import org.buildmosaic.core.instrumentation.ExecutionOutcome
import org.buildmosaic.core.instrumentation.InstrumentationCalls
import org.buildmosaic.core.instrumentation.MosaicInstrumentation
import org.buildmosaic.core.instrumentation.ProducerReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Translates runtime reservation, handoff, and execution facts into the provider lifecycle. */
internal class ExecutionProvenance(provider: MosaicInstrumentation) {
  private val calls = InstrumentationCalls(provider)

  fun reserve(): ReservationProvenance {
    val producer = ProducerReference(calls)
    val identity = currentExecution()?.identity
    val caller = calls.invoke { calls.provider.captureCaller(identity) }
    return ReservationProvenance(producer, caller)
  }

  fun reuse(producer: ProducerReference) {
    currentExecution()?.dependency(producer)
  }

  fun batch(): BatchProvenance = BatchProvenance(calls)

  fun startSingle(
    name: String?,
    reservation: ReservationProvenance,
  ): ObservedExecution? {
    val caller = reservation.takeCaller()
    val owner = observe(calls.invoke { calls.provider.startSingle(name, caller) })
    publish(reservation.producer, owner)
    return owner
  }

  fun startBatch(
    name: String?,
    size: Int,
    batch: BatchProvenance,
  ): ObservedExecution? = observe(batch.start(name, size))

  fun publish(
    producer: ProducerReference,
    owner: ObservedExecution?,
  ) {
    val identity = owner?.identity
    if (identity == null) producer.abandon() else producer.publish(identity)
  }

  private fun observe(execution: MosaicInstrumentation.Execution?): ObservedExecution? =
    execution?.let { ObservedExecution(calls, it) }

  private fun currentExecution(): ObservedExecution? = ObservedExecution.currentFor(calls.provider)
}

/** Caller state lives only until contribution/start; cache entries retain just the producer. */
internal class ReservationProvenance(
  val producer: ProducerReference,
  private var caller: MosaicInstrumentation.CallerContext?,
) {
  fun takeCaller(): MosaicInstrumentation.CallerContext? = caller.also { caller = null }

  fun abandon() {
    caller = null
    producer.abandonIfUnresolved()
  }
}

/** Provider accumulator translation; the runtime serializes calls and decides handoff/abandonment. */
internal class BatchProvenance(private val calls: InstrumentationCalls) {
  private var initialized = false
  private var collector: MosaicInstrumentation.Batch? = null

  fun contribute(reservation: ReservationProvenance) {
    if (!initialized) {
      initialized = true
      collector = calls.invoke { calls.provider.createBatch() }
    }
    val caller = reservation.takeCaller()
    collector?.let { calls.invoke { it.contribute(caller) } }
  }

  fun start(
    name: String?,
    size: Int,
  ): MosaicInstrumentation.Execution? {
    val accumulator = collector.also { collector = null } ?: return null
    val execution = calls.invoke { accumulator.start(name, size) }
    if (execution == null) calls.invoke { accumulator.abandon() }
    return execution
  }

  fun abandon() {
    val accumulator = collector.also { collector = null }
    if (accumulator != null) calls.invoke { accumulator.abandon() }
  }
}

/** Owns observed identity, guarded context, actual completion, and exactly-once provider finalization. */
internal class ObservedExecution(
  private val calls: InstrumentationCalls,
  private val execution: MosaicInstrumentation.Execution,
) : ThreadContextElement<ObservedExecution?> {
  companion object Key : CoroutineContext.Key<ObservedExecution> {
    private val current = ThreadLocal<ObservedExecution?>()

    fun currentFor(provider: MosaicInstrumentation): ObservedExecution? =
      current.get()?.takeIf { it.active && it.calls.provider === provider }
  }

  val identity = calls.invoke { execution.identity }

  @Volatile private var active = true
  private var completion: ExecutionCompletion? = null
  private var finalized = false
  override val key: CoroutineContext.Key<*> = Key

  fun context(): CoroutineContext = if (identity == null) EmptyCoroutineContext else calls.context(execution) + this

  fun dependency(producer: ProducerReference) {
    calls.invoke { execution.dependency(producer) }
  }

  fun completed(failure: Throwable?) {
    active = false
    if (completion != null) return
    val outcome =
      when (failure) {
        null -> ExecutionOutcome.SUCCESS
        is CancellationException -> ExecutionOutcome.CANCELLED
        else -> ExecutionOutcome.FAILURE
      }
    completion = ExecutionCompletion(outcome, failure?.javaClass?.name, System.nanoTime())
  }

  /** Called after the execution context has exited and application results have been published. */
  fun finish() {
    if (finalized) return
    val recorded = checkNotNull(completion) { "Execution ended without completion" }
    finalized = true
    calls.invoke { execution.complete(recorded) }
  }

  override fun updateThreadContext(context: CoroutineContext): ObservedExecution? =
    current.get().also { current.set(if (active) this else null) }

  override fun restoreThreadContext(
    context: CoroutineContext,
    oldState: ObservedExecution?,
  ) {
    if (oldState == null) current.remove() else current.set(oldState)
  }
}
