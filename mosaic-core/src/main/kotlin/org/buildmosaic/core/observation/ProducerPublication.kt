package org.buildmosaic.core.observation

/** The lock commits state only. Neither token equality nor callbacks run inside it. */
internal class ProducerPublication(private val calls: ObservationCalls) : ProducerReference {
  private val monitor = Any()
  private var started = false
  private var terminal: ProducerResolution? = null
  private val listeners = mutableListOf<Listener>()

  override val resolution: ProducerResolution? get() = synchronized(monitor) { terminal }

  fun begin() = synchronized(monitor) { started = true }

  fun publish(identity: ExecutionIdentity?) {
    resolve(
      identity?.let { ProducerResolution.Published(it) }
        ?: ProducerResolution.Unavailable(UnavailableReason.NOT_OBSERVED),
    )
  }

  fun startFailed() = resolve(ProducerResolution.Unavailable(UnavailableReason.START_FAILED))

  fun abandon() {
    prepareAbandon()?.invoke()
  }

  /** Commit under the runtime admission lock too; invoke the notification outside all locks. */
  fun prepareAbandon(): (() -> Unit)? =
    synchronized(monitor) {
      if (started || terminal != null) return null
      val notification = commit(ProducerResolution.Unavailable(UnavailableReason.NEVER_STARTED))
      return { notifyListeners(notification) }
    }

  override fun subscribe(listener: (ProducerResolution) -> Unit): AutoCloseable {
    val registration = Listener(listener)
    val resolved =
      synchronized(monitor) {
        terminal.also { if (it == null) listeners.add(registration) }
      }
    if (resolved != null) registration.deliver(resolved)
    return AutoCloseable {
      synchronized(monitor) {
        registration.callback = null
        listeners.remove(registration)
      }
    }
  }

  private fun resolve(resolution: ProducerResolution) {
    val notification =
      synchronized(monitor) {
        if (terminal != null) return
        commit(resolution)
      }
    notifyListeners(notification)
  }

  private fun commit(resolution: ProducerResolution): Pair<ProducerResolution, List<Listener>> {
    terminal = resolution
    return resolution to listeners.toList().also { listeners.clear() }
  }

  private fun notifyListeners(notification: Pair<ProducerResolution, List<Listener>>) {
    notification.second.forEach { it.deliver(notification.first) }
  }

  private inner class Listener(var callback: ((ProducerResolution) -> Unit)?) {
    fun deliver(resolution: ProducerResolution) {
      val claimed = synchronized(monitor) { callback.also { callback = null } }
      if (claimed != null) calls.guarded(CallbackOperation.PRODUCER_LISTENER) { claimed(resolution) }
    }
  }
}
