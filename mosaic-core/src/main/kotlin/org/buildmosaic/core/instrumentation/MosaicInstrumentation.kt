package org.buildmosaic.core.instrumentation

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Supported runtime instrumentation API for integration authors. Application composition uses
 * the integration's Canvas configuration API rather than implementing this contract.
 *
 * Observes actual executions, independently of cached values. Callbacks must be prompt and nonblocking.
 * Callback exceptions (including cancellation exceptions) are isolated and sent to [onCallbackFailure].
 * Providers own sampling, limits, and finalization. Core supplies Tile names, counts, and opaque
 * provenance state, never keys, Canvas values, results, application exceptions, messages, or stacks.
 * Completion exposes only the exception's JVM class name. Applications must deliberately instrument
 * any payload they want an integration to observe; installing a provider does not grant payload access.
 * Callbacks may synchronously reenter composition; they must not wait for the work they reserve.
 */
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

  /**
   * Creates bounded contributor state for one pending batch. Returning null declines observation.
   * Returning an accumulator transfers its lifecycle to core: [Batch.start] is called at most once,
   * and [Batch.abandon] is called exactly once when the accumulator is not used by an execution.
   * Providers release any state they allocate before throwing or returning null.
   */
  fun createBatch(): Batch?

  /**
   * A provider-owned accumulator. Core serializes [contribute] calls with the pending batch handoff,
   * including synchronous reentry. Callbacks for the same accumulator never overlap or recursively
   * invoke one another.
   * After that handoff no more contributors are added; [start] is called at most once.
   * Implementations may deduplicate, sample, or truncate contributors without retaining caller lists.
   */
  interface Batch {
    /** One call per request reserving any new keys, including callers with no captured context. */
    fun contribute(caller: CallerContext?)

    /**
     * Starts the real batch, with its fixed contributors. [batchSize] is the number of distinct newly
     * fetched keys passed to this execution's MultiTile block; key values are never supplied.
     * Returning null declines observation.
     */
    fun start(
      name: String?,
      batchSize: Int,
    ): Execution?

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

  /**
   * Diagnostics for failures originating in provider callbacks, including provider context elements
   * and publication subscribers. These may contain the provider's raw exception; application Tile
   * exceptions never reach this callback. Failures of this callback are also contained.
   */
  fun onCallbackFailure(failure: Throwable)
}

/** Actual execution outcome supplied to runtime integrations, with cancellation distinct from failure. */
enum class ExecutionOutcome { SUCCESS, FAILURE, CANCELLED }

/**
 * Actual Tile completion supplied to runtime integrations, independent of result awaiting and
 * adapter finalization.
 * [errorType] is the exception's JVM binary class name, or null on success. It contains no application
 * exception instance, message, stack trace, cause, suppressed exception, or structured payload.
 * Cancellation remains distinguishable through [outcome].
 */
class ExecutionCompletion internal constructor(
  val outcome: ExecutionOutcome,
  val errorType: String?,
  val completedAtNanos: Long,
)
