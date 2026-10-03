package org.buildmosaic.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.observation.CallerSnapshot
import org.buildmosaic.core.observation.Contributors
import org.buildmosaic.core.observation.ExecutionKind
import org.buildmosaic.core.observation.ExecutionStart
import org.buildmosaic.core.observation.ObservationCalls
import org.buildmosaic.core.observation.ObservedExecution
import org.buildmosaic.core.observation.ProducerPublication
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Request-scoped Tile execution, caching, and batching, with optional execution observation.
 *
 * @param canvas Dependency bindings and immutable runtime configuration.
 * @param dispatcher Dispatcher for all executions, whether observed or unobserved.
 */
@Suppress("LargeClass")
open class MosaicImpl(
  override val canvas: Canvas,
  dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Mosaic, CoroutineScope {
  private val job = SupervisorJob()
  override val coroutineContext: CoroutineContext = job + dispatcher
  private val observation = canvas.runtimeConfig.executionObserver?.let { ObservationCalls(it) }
  private val executionMonitor = Any()
  private val singleCache = ConcurrentHashMap<Tile<*>, CacheEntry<*>>()
  private val multiStates = ConcurrentHashMap<MultiTile<*, *>, MultiTileState<*, *>>()

  @Suppress("UNCHECKED_CAST")
  override fun <V> composeAsync(tile: Tile<V>): Deferred<V> {
    val cached = singleCache[tile] as CacheEntry<V>?
    if (cached != null) return consume(cached)
    val entry = newEntry<V>(observation?.let { ProducerPublication(it) })
    val previous = singleCache.putIfAbsent(tile, entry) as CacheEntry<V>?
    if (previous != null) {
      entry.result.cancel()
      entry.producer?.abandon()
      return consume(previous)
    }
    // A same-Tile capture reentry now sees the winning result and producer, without recapturing.
    val caller = if (job.isActive) observation?.capture() else null
    launchSingle(tile, entry, caller)
    return consume(entry)
  }

  @Suppress("UNCHECKED_CAST", "NestedBlockDepth")
  override fun <K : Any, V> composeAsync(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, Deferred<V>> {
    if (keys.isEmpty()) return emptyMap()
    val state = multiStates.computeIfAbsent(tile) { MultiTileState<K, V>() } as MultiTileState<K, V>
    val result = HashMap<K, Deferred<V>>(keys.size)
    val consumed = if (observation != null) ArrayList<ProducerPublication>() else null
    val winners = ArrayList<Pair<K, CacheEntry<V>>>()
    var producer: ProducerPublication? = null
    try {
      for (key in keys) {
        val existing = state.cache[key]
        val entry =
          if (existing != null) {
            existing
          } else {
            if (producer == null) producer = observation?.let { ProducerPublication(it) }
            reserve(state, key, producer, winners)
          }
        result[key] = entry.result
        entry.producer?.let { consumed?.add(it) }
      }
    } finally {
      // Earlier winners remain owned even when later application key code throws.
      acceptReservations(tile, state, winners, producer)
    }
    consumed?.forEach { ObservedExecution.current()?.dependency(it) }
    return result
  }

  private fun <K : Any, V> acceptReservations(
    tile: MultiTile<K, V>,
    state: MultiTileState<K, V>,
    winners: List<Pair<K, CacheEntry<V>>>,
    producer: ProducerPublication?,
  ) {
    if (winners.isEmpty()) {
      producer?.abandon()
      return
    }
    val caller = if (job.isActive) observation?.capture() else null
    val owner = state.enqueue(ReservationGroup(winners, producer), caller)
    // Neither the group nor this launch retains an overflow caller snapshot.
    if (owner != null) launchPending(tile, state, owner)
  }

  @Suppress("TooGenericExceptionCaught")
  private fun <K : Any, V> reserve(
    state: MultiTileState<K, V>,
    key: K,
    producer: ProducerPublication?,
    winners: MutableList<Pair<K, CacheEntry<V>>>,
  ): CacheEntry<V> {
    val candidate = newEntry<V>(producer)
    val previous =
      try {
        state.cache.putIfAbsent(key, candidate)
      } catch (failure: Throwable) {
        candidate.result.cancel()
        throw failure
      }
    if (previous == null) winners.add(key to candidate) else candidate.result.cancel()
    return previous ?: candidate
  }

  private fun <V> newEntry(producer: ProducerPublication?): CacheEntry<V> {
    val result = CompletableDeferred<V>(job)
    if (producer != null) {
      result.invokeOnCompletion {
        if (!job.isActive) {
          val notification = synchronized(executionMonitor) { producer.prepareAbandon() }
          notification?.invoke()
        }
      }
    }
    return CacheEntry(result, producer)
  }

  private fun <V> consume(entry: CacheEntry<V>): Deferred<V> {
    entry.producer?.let { ObservedExecution.current()?.dependency(it) }
    return entry.result
  }

  @Suppress("TooGenericExceptionCaught")
  private fun <V> launchSingle(
    tile: Tile<V>,
    entry: CacheEntry<V>,
    caller: CallerSnapshot?,
  ) {
    var capturedCaller = caller
    try {
      launch {
        val contributors = capturedCaller?.let { Contributors(it, emptyList(), 1) }
        capturedCaller = null
        executeWork(
          contributors?.let { ExecutionStart(ExecutionKind.SINGLE, null, it, System.nanoTime()) },
          listOfNotNull(entry.producer),
          {
            entry.result.completeExceptionally(it)
          },
        ) {
          val value = tile.block(this@MosaicImpl)
          ObservedExecution.withoutCaller { entry.result.complete(value) }
          null
        }
      }.invokeOnCompletion { failure ->
        capturedCaller = null
        if (failure != null) entry.result.completeExceptionally(failure)
        entry.producer?.abandon()
      }
    } catch (failure: Throwable) {
      capturedCaller = null
      entry.result.completeExceptionally(failure)
      entry.producer?.abandon()
      throw failure
    }
  }

  @Suppress("TooGenericExceptionCaught")
  private fun <K : Any, V> launchPending(
    tile: MultiTile<K, V>,
    state: MultiTileState<K, V>,
    owner: PendingBatch<K, V>,
  ) {
    try {
      launch {
        state.takePending(owner)?.let { executeBatch(tile, it) }
      }.invokeOnCompletion { failure ->
        if (failure != null) state.failPending(owner, failure)
      }
    } catch (failure: Throwable) {
      state.failPending(owner, failure)
      throw failure
    }
  }

  @Suppress("TooGenericExceptionCaught")
  private suspend fun <K : Any, V> executeBatch(
    tile: MultiTile<K, V>,
    batch: PendingBatch<K, V>,
  ) {
    try {
      // Application key code runs outside the pending monitor, before an execution is fabricated.
      val keys = LinkedHashSet<K>()
      batch.groups.forEach { group -> group.winners.forEach { keys.add(it.first) } }
      executeWork(
        batch.takeContributors()?.let { ExecutionStart(ExecutionKind.MULTI, keys.size, it, System.nanoTime()) },
        batch.groups.mapNotNull { it.producer },
        batch::fail,
      ) {
        val values = tile.block(this@MosaicImpl, keys)
        var missing: Throwable? = null
        batch.groups.forEach { group ->
          group.winners.forEach { (key, entry) ->
            val value = values[key]
            if (value != null) {
              ObservedExecution.withoutCaller { entry.result.complete(value) }
            } else {
              val failure = NoSuchElementException("Batch result missing key $key")
              missing = missing ?: failure
              ObservedExecution.withoutCaller { entry.result.completeExceptionally(failure) }
            }
          }
        }
        missing
      }
    } catch (failure: Throwable) {
      batch.fail(failure)
    }
  }

  @Suppress("TooGenericExceptionCaught")
  private suspend fun executeWork(
    start: ExecutionStart?,
    producers: List<ProducerPublication>,
    fail: (Throwable) -> Unit,
    block: suspend () -> Throwable?,
  ) {
    currentCoroutineContext().ensureActive()
    if (producers.isNotEmpty()) {
      // Fixed-batch admission and cancellation claim all group origins atomically.
      synchronized(executionMonitor) {
        job.ensureActive()
        producers.forEach { it.begin() }
      }
    }
    val attempt = start?.let { observation?.start(it) }
    val started = attempt?.getOrNull()
    val execution = started?.let { ObservedExecution(checkNotNull(observation), it) }
    producers.forEach { if (attempt == null) it.startFailed() else it.publish(started?.identity) }
    var applicationFailure: Throwable? = null
    val context =
      execution?.context
        ?: if (ObservedExecution.current() != null) ObservedExecution.unobservedContext else EmptyCoroutineContext
    try {
      withContext(context) {
        try {
          currentCoroutineContext().ensureActive()
          applicationFailure = block()
        } catch (failure: Throwable) {
          applicationFailure = failure
          ObservedExecution.withoutCaller { fail(failure) }
        }
      }
      // Scope completion includes attached children, independently of earlier result publication.
      execution?.recordCompletion(applicationFailure)
    } catch (failure: Throwable) {
      // Keep original body failures; cancellation may instead be caused by a failing child.
      val original = applicationFailure?.takeUnless { it is CancellationException } ?: failure
      execution?.recordCompletion(original)
      ObservedExecution.withoutCaller { fail(original) }
    } finally {
      execution?.finish()
    }
  }
}
