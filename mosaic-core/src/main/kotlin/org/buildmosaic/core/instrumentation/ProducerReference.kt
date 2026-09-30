@file:OptIn(ExperimentalMosaicInstrumentation::class)

package org.buildmosaic.core.instrumentation

/**
 * A cache producer's publication, visible before an execution identity exists.
 * It transitions once from unresolved ([resolution] == null) to [Published] or [Abandoned].
 * Core owns transitions. Subscribers may race publication and must not block in their callbacks.
 */
@ExperimentalMosaicInstrumentation
class ProducerReference internal constructor(private val calls: InstrumentationCalls) {
  sealed interface Resolution

  class Published internal constructor(val identity: MosaicInstrumentation.ExecutionIdentity) : Resolution

  data object Abandoned : Resolution

  private val monitor = Any()
  private var terminal: Resolution? = null
  private var listeners: MutableSet<Subscription>? = null

  val resolution: Resolution? get() = synchronized(monitor) { terminal }

  /**
   * Invokes [listener] once on resolution, including immediate notification for a resolved reference.
   * Closing the handle releases a pending listener; a notification already taken may still run.
   * Both resolution and closing release callback state. Providers must bound active subscriptions.
   */
  fun subscribe(listener: (Resolution) -> Unit): AutoCloseable {
    val subscription = Subscription(listener)
    val resolved =
      synchronized(monitor) {
        terminal.also {
          if (it == null) {
            val pending = listeners ?: LinkedHashSet<Subscription>().also { listeners = it }
            pending.add(subscription)
          }
        }
      }
    if (resolved != null) subscription.notify(resolved)
    return subscription
  }

  internal fun publish(identity: MosaicInstrumentation.ExecutionIdentity) = resolve(Published(identity), false)

  internal fun abandon() = resolve(Abandoned, false)

  // Job cleanup may follow a successful body, a declined observation, or an earlier launch failure.
  internal fun abandonIfUnresolved() = resolve(Abandoned, true)

  private fun resolve(
    resolution: Resolution,
    onlyIfUnresolved: Boolean,
  ) {
    val pending =
      synchronized(monitor) {
        if (onlyIfUnresolved && terminal != null) return
        check(terminal == null) { "Producer publication already resolved" }
        terminal = resolution
        listeners.also { listeners = null }
      }
    pending?.forEach { it.notify(resolution) }
  }

  private inner class Subscription(private var listener: ((Resolution) -> Unit)?) : AutoCloseable {
    override fun close() {
      synchronized(monitor) {
        listener = null
        listeners?.remove(this)
        if (listeners?.isEmpty() == true) listeners = null
      }
    }

    fun notify(resolution: Resolution) {
      val callback = synchronized(monitor) { listener.also { listener = null } }
      if (callback != null) calls.invoke { callback(resolution) }
    }
  }
}
