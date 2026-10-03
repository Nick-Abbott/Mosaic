package org.buildmosaic.opentelemetry

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import org.buildmosaic.core.observation.ExecutionIdentity
import java.time.Instant
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

internal val contributorLink: Attributes = Attributes.of(AttributeKey.stringKey("mosaic.link.type"), "contributor")
internal val dependencyLink: Attributes = Attributes.of(AttributeKey.stringKey("mosaic.link.type"), "dependency")

/** Only immutable span identity is retained by Mosaic's cache, never a Span or captured Context. */
internal class SpanIdentity(val context: SpanContext) : ExecutionIdentity {
  // Snapshot identifiers before entering relationship-state locks, including for custom API providers.
  private val traceId = context.traceId
  private val spanId = context.spanId

  // Remote flags and sampling state do not change the identity of the relationship target.
  override fun equals(other: Any?): Boolean =
    other is SpanIdentity && traceId == other.traceId && spanId == other.spanId

  override fun hashCode(): Int = 31 * traceId.hashCode() + spanId.hashCode()
}

/** One wall-clock anchor per execution; duration and delayed end time stay on Mosaic's monotonic clock. */
internal class SpanClock(startedAtNanos: Long) {
  private val offset: Long

  init {
    val before = System.nanoTime()
    val now = Instant.now()
    val after = System.nanoTime()
    offset = TimeUnit.SECONDS.toNanos(now.epochSecond) + now.nano - (before + (after - before) / 2)
  }

  val start: Long = epochNanos(startedAtNanos)

  fun epochNanos(monotonicNanos: Long): Long = offset + monotonicNanos
}

/** Bounds unresolved relationships across the installation, including completed spans awaiting origins. */
internal class SubscriptionBudget {
  private val permits = Semaphore(1024)

  fun acquire(): Boolean = permits.tryAcquire()

  fun release() = permits.release()
}

/** SDK/provider code is outside Mosaic and adapter locks. Diagnostics contain no exception payload. */
internal object TelemetryCalls {
  private val logger = System.getLogger("org.buildmosaic.opentelemetry")

  fun <T> safely(block: () -> T): T? = runCatching(block).onFailure { report("SDK", it.javaClass.name) }.getOrNull()

  fun report(
    operation: String,
    exceptionClassName: String?,
  ) {
    runCatching {
      logger.log(
        System.Logger.Level.WARNING,
        "Mosaic telemetry callback: $operation ($exceptionClassName)",
      )
    }
  }
}
