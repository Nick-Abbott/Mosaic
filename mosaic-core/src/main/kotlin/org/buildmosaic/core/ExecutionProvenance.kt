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
    val caller = currentExecution() ?: return
    calls.invoke { caller.execution.dependency(producer) }
  }

  fun batch(): BatchProvenance = BatchProvenance(calls)

  fun startSingle(
    name: String?,
    reservation: ReservationProvenance,
  ): ExecutionOwner? {
    val caller = reservation.takeCaller()
    val owner = observe(calls.invoke { calls.provider.startSingle(name, caller) })
    publish(reservation.producer, owner)
    return owner
  }

  fun startBatch(
    name: String?,
    size: Int,
    batch: BatchProvenance,
  ): ExecutionOwner? = observe(batch.start(name, size))

  fun publish(
    producer: ProducerReference,
    owner: ExecutionOwner?,
  ) {
    val identity = owner?.identity
    if (identity == null) producer.abandon() else producer.publish(identity)
  }

  fun context(owner: ExecutionOwner): CoroutineContext =
    if (owner.identity == null) EmptyCoroutineContext else calls.context(owner.execution) + owner

  fun complete(owner: ExecutionOwner) {
    val completion = checkNotNull(owner.completion) { "Execution ended without completion" }
    calls.invoke { owner.execution.complete(completion) }
  }

  private fun observe(execution: MosaicInstrumentation.Execution?): ExecutionOwner? =
    execution?.let { ExecutionOwner(calls, it, calls.invoke { it.identity }) }

  private fun currentExecution(): ExecutionOwner? =
    ExecutionOwner.current.get()?.takeIf { it.active && it.calls.provider === calls.provider }
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

/** Mutated under the runtime's pending-batch monitor; owns no keys, values, scheduling, or cache. */
internal class BatchProvenance(private val calls: InstrumentationCalls) {
  private val waiting = ArrayDeque<ReservationProvenance>()
  private var initialized = false
  private var collector: MosaicInstrumentation.Batch? = null
  private var terminal = false
  private var abandoning = false
  var collecting = false
    private set

  fun contribute(reservation: ReservationProvenance) {
    waiting.addLast(reservation)
    if (collecting) return
    collecting = true
    try {
      // Runtime ownership is already published. Synchronous reentry queues rather than recursing.
      if (!initialized) {
        initialized = true
        collector = calls.invoke { calls.provider.createBatch() }
      }
      while (waiting.isNotEmpty() && !abandoning) {
        val caller = waiting.removeFirst().takeCaller()
        collector?.let { calls.invoke { it.contribute(caller) } }
      }
    } finally {
      collecting = false
    }
  }

  fun requestAbandonment() {
    abandoning = true
  }

  fun start(
    name: String?,
    size: Int,
  ): MosaicInstrumentation.Execution? {
    check(!collecting && !terminal) { "Invalid contributor handoff" }
    terminal = true
    val accumulator = collector.also { collector = null } ?: return null
    val execution = calls.invoke { accumulator.start(name, size) }
    if (execution == null) calls.invoke { accumulator.abandon() }
    return execution
  }

  fun abandon() {
    check(!collecting && !terminal) { "Invalid contributor abandonment" }
    terminal = true
    waiting.forEach { it.takeCaller() }
    waiting.clear()
    val accumulator = collector.also { collector = null }
    if (accumulator != null) calls.invoke { accumulator.abandon() }
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
  var completion: ExecutionCompletion? = null
    private set
  override val key: CoroutineContext.Key<*> = Key

  fun completed(failure: Throwable?) {
    if (completion != null) return
    val outcome =
      when (failure) {
        null -> ExecutionOutcome.SUCCESS
        is CancellationException -> ExecutionOutcome.CANCELLED
        else -> ExecutionOutcome.FAILURE
      }
    completion = ExecutionCompletion(outcome, failure?.javaClass?.name, System.nanoTime())
  }

  override fun updateThreadContext(context: CoroutineContext): ExecutionOwner? =
    current.get().also { current.set(if (active) this else null) }

  override fun restoreThreadContext(
    context: CoroutineContext,
    oldState: ExecutionOwner?,
  ) {
    if (oldState == null) current.remove() else current.set(oldState)
  }
}
