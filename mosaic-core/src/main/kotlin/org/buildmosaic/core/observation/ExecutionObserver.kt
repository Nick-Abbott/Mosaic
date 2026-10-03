package org.buildmosaic.core.observation

import org.buildmosaic.core.injection.CanvasBuilder
import java.util.Collections

/** Opaque integration-owned caller snapshot. Prefer immutable, lightweight snapshots. */
interface CallerContext

/** Opaque identity of an actual observed execution, retained by cached producer references. */
interface ExecutionIdentity

/**
 * Supported integration boundary for actual Tile executions, not compose calls or individual batch keys.
 * All callbacks are synchronous, exception-guarded, and must return promptly. They must not block on
 * Mosaic progress. The integration owns asynchronous export, finalization, and provider shutdown.
 * Callbacks can run concurrently across executions and child coroutines; integrations coordinate
 * their own mutable state.
 *
 * Mosaic supplies only structural data and opaque integration-owned tokens: no keys, values, Canvas
 * contents, request identifiers, or Throwable objects. Integrations control their own token contents.
 * Callback reentry uses normal composition, but does not inherit the surrounding core execution
 * relationship. Provider ambient context is left intact; nested actual executions establish their own
 * core relationship. Caller identities and producer dependencies are forwarded only between Mosaics
 * sharing this exact observer instance. Independently installed observers exchange no core tokens;
 * capturing an integration's own ambient context remains independent. Mosaic neither hashes nor
 * compares provider tokens.
 */
interface ExecutionObserver {
  /** Called only after winning reservations are visible, before acceptance/scheduling. */
  fun captureCaller(): CallerContext?

  /** Receives a fixed execution description. Returning null opts out of this execution's observation. */
  fun onStart(start: ExecutionStart): StartedObservation?

  /** Sanitized provider diagnostics. Failures here are contained without recursive reporting. */
  fun onCallbackFailure(failure: CallbackFailure) {}
}

/** Immutable start result; accessing its fields invokes no provider code. */
class StartedObservation(
  val callbacks: ExecutionObservation,
  val identity: ExecutionIdentity? = null,
  val context: ExecutionContext? = null,
)

/** Execution-local callbacks, subject to the same contract as [ExecutionObserver]. */
interface ExecutionObservation {
  /** A composed result's origin, possibly still unresolved; never await its resolution here. */
  fun onDependency(producer: ProducerReference) {}

  /**
   * Called once after the execution scope, including attached child coroutines, finishes and its
   * context exits. Results may already be published; a later child failure cannot replace them.
   * Unresolved producers do not delay this notification. External finalization may retain the actual
   * completion timestamp.
   */
  fun onComplete(completion: ExecutionCompletion)
}

/**
 * Installs only integration-owned ambient state. Each invocation returns its own restoration token.
 * Close restores the preceding state on that installation's thread. Installations can nest and run
 * concurrently, including overlapping coroutine update/restore calls; never share a latest-token slot.
 * If install partially mutates state and throws, the provider must restore its partial mutation itself.
 * Mosaic cannot reconstruct unknown state. Neither install nor close may block on Mosaic progress.
 */
fun interface ExecutionContext {
  fun install(): AutoCloseable
}

/** The current actual execution identity, independently of any reservation it produced. */
class CallerSnapshot internal constructor(
  val execution: ExecutionIdentity?,
  val context: CallerContext?,
) {
  /** Projects opaque tokens for an integration composing existing observers. */
  fun copy(
    execution: ExecutionIdentity? = this.execution,
    context: CallerContext? = this.context,
  ): CallerSnapshot = CallerSnapshot(execution, context)
}

/**
 * First accepted caller plus at most 63 additional callers, in acceptance order, without deduplication.
 * Empty/failed captures retain their positions. Counts include all accepted nonempty reservation groups;
 * truncation affects observation metadata only. The lists are immutable snapshots.
 */
class Contributors internal constructor(
  val initiating: CallerSnapshot,
  additional: List<CallerSnapshot>,
  val totalCount: Long,
) {
  val additional: List<CallerSnapshot> = Collections.unmodifiableList(ArrayList(additional))
  val truncated: Boolean get() = totalCount > RETAINED_CONTRIBUTORS

  internal companion object {
    const val RETAINED_CONTRIBUTORS = 64
  }
}

/** Contains no Tile object, label, application keys, or values. Naming is outside this API slice. */
class ExecutionStart internal constructor(
  val kind: ExecutionKind,
  val batchSize: Int?,
  val contributors: Contributors,
  val startedAtNanos: Long,
) {
  /**
   * Projects every retained caller without changing structural data, order, counts, or truncation.
   * The returned description owns an immutable copy. The transform runs synchronously in the caller;
   * it is integration code, with no Mosaic lock held.
   */
  fun mapCallers(transform: (CallerSnapshot) -> CallerSnapshot): ExecutionStart =
    ExecutionStart(
      kind,
      batchSize,
      Contributors(transform(contributors.initiating), contributors.additional.map(transform), contributors.totalCount),
      startedAtNanos,
    )
}

enum class ExecutionKind { SINGLE, MULTI }

enum class ExecutionOutcome { SUCCESS, FAILURE, CANCELLATION }

/** No exception messages, causes, stacks, suppressed exceptions, or raw Throwable cross this boundary. */
class ExecutionCompletion internal constructor(
  val outcome: ExecutionOutcome,
  val completedAtNanos: Long,
  val exceptionClassName: String?,
)

/** Fixed callback site and optional JVM binary exception class name, with no free-form error details. */
class CallbackFailure internal constructor(
  val operation: CallbackOperation,
  val exceptionClassName: String?,
)

enum class CallbackOperation { CAPTURE, START, DEPENDENCY, COMPLETE, INSTALL, RESTORE, PRODUCER_LISTENER }

/**
 * Integration-author installation hook, separate from the ordinary Canvas dependency DSL.
 * Installs one supported observer for this Canvas and all descendants. Local, inherited, and reentrant
 * duplicates fail before another factory executes. Failed factories leave configuration unchanged.
 * Canvas never owns provider shutdown. Import this extension from the observation package explicitly.
 */
fun CanvasBuilder.installExecutionObserver(factory: () -> ExecutionObserver) {
  configureExecutionObserver(factory)
}
