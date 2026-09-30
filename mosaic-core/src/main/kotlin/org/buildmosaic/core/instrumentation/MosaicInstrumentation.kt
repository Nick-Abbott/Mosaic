package org.buildmosaic.core.instrumentation

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Provider SPI for runtime integrations. Application composition does not require this API. */
@RequiresOptIn(message = "The Mosaic instrumentation SPI is experimental.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
annotation class ExperimentalMosaicInstrumentation

/**
 * Observes actual executions, independently of cached values. Callbacks must be prompt and nonblocking.
 * Callback exceptions (including cancellation exceptions) are isolated and sent to [onCallbackFailure].
 * Providers own sampling, limits, and finalization; core supplies no keys, Canvas values, or results.
 */
@ExperimentalMosaicInstrumentation
interface MosaicInstrumentation {
  /** Opaque captured caller state. Providers should retain only bounded, necessary context. */
  interface CallerContext

  /** Opaque identity retained by cache publications; it must not retain an execution's mutable state. */
  interface ExecutionIdentity

  /**
   * Captures the current caller before reserving work. [execution] is present only for a current
   * Mosaic-owned execution using this provider. External callers may supply ambient context here.
   */
  fun captureCaller(execution: ExecutionIdentity?): CallerContext?

  /** Starts the winning SingleTile execution. Returning null declines observation. */
  fun startSingle(
    name: String?,
    caller: CallerContext?,
  ): Execution?

  /** Creates bounded contributor state for one pending batch. Returning null declines observation. */
  fun createBatch(): Batch?

  /**
   * A provider-owned accumulator. Core serializes [contribute] calls with the pending batch handoff.
   * After that handoff no more contributors are added; [start] is called at most once.
   * Implementations may deduplicate, sample, or truncate contributors without retaining caller lists.
   */
  interface Batch {
    /** One call per request reserving any new keys, including callers with no captured context. */
    fun contribute(caller: CallerContext?)

    /** Starts the real batch, with its fixed contributors. Returning null declines observation. */
    fun start(name: String?): Execution?

    /** Releases contributor state when work never starts, or [start] declines observation or throws. */
    fun abandon()
  }

  interface Execution {
    val identity: ExecutionIdentity

    /**
     * Optional context for the Tile body and its structured children. Only [ThreadContextElement]
     * elements are accepted. Core installs guarded elements under private keys, so providers read
     * their own ambient state rather than looking up these elements in the coroutine context.
     * Jobs, dispatchers, handlers, and other elements are rejected as adapter failures.
     */
    val coroutineContext: CoroutineContext get() = EmptyCoroutineContext

    /**
     * This Mosaic-owned execution consumes an existing reservation, whether in flight or completed.
     * A reference may still be unresolved. Repeated reports are allowed; providers bound/deduplicate
     * retained dependencies and close subscriptions they no longer need. External callers get no report.
     */
    fun dependency(producer: ProducerReference)

    /**
     * Exactly one actual completion notification, outside the execution context and after publishing
     * available results. [ExecutionCompletion.completedAtNanos] uses [System.nanoTime]. This callback
     * must return without waiting for producer references; telemetry finalization may happen later.
     */
    fun complete(completion: ExecutionCompletion)
  }

  /** Diagnostics for failed adapter callbacks. Failures of this callback are also contained. */
  fun onCallbackFailure(failure: Throwable)
}

@ExperimentalMosaicInstrumentation
enum class ExecutionOutcome { SUCCESS, FAILURE, CANCELLED }

/** Actual Tile completion, independent of result awaiting and adapter finalization. */
@ExperimentalMosaicInstrumentation
class ExecutionCompletion internal constructor(
  val outcome: ExecutionOutcome,
  val failure: Throwable?,
  val completedAtNanos: Long,
)
