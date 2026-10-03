package org.buildmosaic.opentelemetry

/** Handles immediate subscribe notification and notification racing handle publication. */
internal class PendingProducer(private val budget: SubscriptionBudget) {
  private var finished = false
  private var handle: AutoCloseable? = null

  fun attach(subscription: AutoCloseable) {
    val close =
      synchronized(this) {
        if (!finished) handle = subscription
        finished
      }
    if (close) TelemetryCalls.safely { subscription.close() }
  }

  fun finish() {
    val subscription =
      synchronized(this) {
        if (finished) return
        finished = true
        handle.also { handle = null }
      }
    budget.release()
    subscription?.let { TelemetryCalls.safely { it.close() } }
  }
}
