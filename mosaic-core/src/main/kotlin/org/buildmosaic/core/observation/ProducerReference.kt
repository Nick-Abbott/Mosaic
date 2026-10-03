package org.buildmosaic.core.observation

/**
 * Core-owned origin of a cached reservation. Transitions once from unresolved to a terminal resolution.
 * Several references may resolve to the same MultiTile execution identity. Completed cache reuse keeps
 * the same reference; it does not manufacture another execution.
 */
interface ProducerReference {
  /** Null until terminal; inspecting this property does not wait. */
  val resolution: ProducerResolution?

  /**
   * Notifications are synchronous, guarded, and outside Mosaic locks. An already terminal reference
   * invokes the listener before this method returns. Closing prevents notification if it wins before
   * invocation is claimed; it neither waits for nor interrupts an already claimed invocation.
   * Callbacks must return promptly without blocking on Mosaic progress.
   */
  fun subscribe(listener: (ProducerResolution) -> Unit): AutoCloseable
}

sealed interface ProducerResolution {
  class Published(val identity: ExecutionIdentity) : ProducerResolution

  class Unavailable(val reason: UnavailableReason) : ProducerResolution
}

enum class UnavailableReason { NEVER_STARTED, NOT_OBSERVED, START_FAILED }
