package org.buildmosaic.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.buildmosaic.core.exception.MosaicMissingMultiTileResultException
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.instrumentation.InstrumentationCalls
import org.buildmosaic.core.instrumentation.MosaicInstrumentation
import org.buildmosaic.core.instrumentation.ProducerReference
import java.util.concurrent.ConcurrentHashMap

/** Separate caches and scheduling code: ordinary Mosaic operations never enter this implementation. */
internal class InstrumentedMosaic(
  canvas: Canvas,
  dispatcher: CoroutineDispatcher,
  instrumentation: MosaicInstrumentation,
) : MosaicImpl(canvas, dispatcher) {
  private val calls = InstrumentationCalls(instrumentation)
  private val runner = ExecutionRunner(calls)
  private val singles = ConcurrentHashMap<Tile<*>, ExecutionEntry<*>>()
  private val multis = ConcurrentHashMap<MultiTile<*, *>, InstrumentedMultiState<*, *>>()

  @Suppress("UNCHECKED_CAST", "TooGenericExceptionCaught")
  override fun <V> composeAsync(tile: Tile<V>): Deferred<V> {
    val existing = singles[tile] as ExecutionEntry<V>?
    if (existing != null) return reuse(existing)
    val caller = captureCaller()
    val producer = ProducerReference(calls)
    val entry = ExecutionEntry(CompletableDeferred<V>(coroutineContext[Job]), producer)
    val previous = singles.putIfAbsent(tile, entry) as ExecutionEntry<V>?
    if (previous != null) {
      entry.value.cancel()
      producer.abandon()
      return reuse(previous)
    }
    try {
      launch { executeSingle(tile, entry, caller) }.invokeOnCompletion { failure ->
        producer.abandonIfUnresolved()
        if (failure != null) entry.value.completeExceptionally(failure)
      }
    } catch (failure: Throwable) {
      producer.abandonIfUnresolved()
      entry.value.completeExceptionally(failure)
      throw failure
    }
    return entry.value
  }

  @Suppress("UNCHECKED_CAST")
  override fun <K : Any, V> composeAsync(
    tile: MultiTile<K, V>,
    keys: Collection<K>,
  ): Map<K, Deferred<V>> {
    if (keys.isEmpty()) return emptyMap()
    val state = multis.computeIfAbsent(tile) { InstrumentedMultiState<K, V>(calls) } as InstrumentedMultiState<K, V>
    val result = HashMap<K, Deferred<V>>(keys.size)
    val winners = ArrayList<Pair<K, ExecutionEntry<V>>>()
    var producer: ProducerReference? = null
    var caller: MosaicInstrumentation.CallerContext? = null
    try {
      for (key in keys) {
        val existing = state.cache[key]
        if (existing != null) {
          result[key] = reuse(existing)
          continue
        }
        // One reference per reserving call, shared by all its winning keys.
        val publication =
          producer ?: ProducerReference(calls).also {
            producer = it
            caller = captureCaller()
          }
        val entry = ExecutionEntry(CompletableDeferred<V>(coroutineContext[Job]), publication)
        val previous = state.cache.putIfAbsent(key, entry)
        if (previous == null) winners += key to entry else entry.value.cancel()
        result[key] = if (previous == null) entry.value else reuse(previous)
      }
    } finally {
      // As on the ordinary path, a later throwing key must not strand earlier reservations.
      if (winners.isEmpty()) {
        producer?.abandon()
      } else {
        val reservation = KeyReservation(winners, checkNotNull(producer), caller)
        val owner = state.enqueue(reservation)
        if (owner != null) launchPending(tile, state, owner)
      }
    }
    return result
  }

  private fun currentExecution(): ExecutionOwner? =
    ExecutionOwner.current.get()?.takeIf { it.active && it.calls.provider === calls.provider }

  private fun captureCaller(): MosaicInstrumentation.CallerContext? =
    calls.invoke { calls.provider.captureCaller(currentExecution()?.identity) }

  private fun <V> reuse(entry: ExecutionEntry<V>): Deferred<V> {
    val caller = currentExecution()
    if (caller != null) calls.invoke { caller.execution.dependency(entry.producer) }
    return entry.value
  }

  private suspend fun <V> executeSingle(
    tile: Tile<V>,
    entry: ExecutionEntry<V>,
    caller: MosaicInstrumentation.CallerContext?,
  ) {
    val execution = runner.observe(calls.invoke { calls.provider.startSingle(tile.name, caller) })
    runner.publish(entry.producer, execution)
    runner.execute(execution, { failure -> entry.value.completeExceptionally(failure) }) { complete ->
      val value = tile.block(this@InstrumentedMosaic)
      complete(null)
      entry.value.complete(value)
    }
  }

  @Suppress("TooGenericExceptionCaught")
  private fun <K : Any, V> launchPending(
    tile: MultiTile<K, V>,
    state: InstrumentedMultiState<K, V>,
    owner: PendingOwner,
  ) {
    try {
      launch {
        val batch = state.takePending(owner)
        if (batch != null) executeBatch(tile, batch)
      }.invokeOnCompletion { failure ->
        if (failure != null) state.failPending(owner, failure)
      }
    } catch (failure: Throwable) {
      state.failPending(owner, failure)
      throw failure
    }
  }

  private suspend fun <K : Any, V> executeBatch(
    tile: MultiTile<K, V>,
    batch: PendingBatch<K, V>,
  ) {
    val keys = batch.prepareKeys(calls) ?: return
    val collector = batch.owner.contributors
    val started = if (collector == null) null else calls.invoke { collector.start(tile.name, keys.size) }
    if (started == null && collector != null) calls.invoke { collector.abandon() }
    val execution = runner.observe(started)
    batch.reservations.forEach { runner.publish(it.producer, execution) }
    runner.execute(execution, { failure -> batch.failValues(failure) }) { complete ->
      val values = tile.block(this@InstrumentedMosaic, keys)
      var missing: Throwable? = null
      batch.reservations.forEach { reservation ->
        reservation.keys.forEach { (key, entry) ->
          val value = values[key]
          if (value != null) {
            entry.value.complete(value)
          } else {
            val failure = MosaicMissingMultiTileResultException(key)
            if (missing == null) missing = failure
            entry.value.completeExceptionally(failure)
          }
        }
      }
      complete(missing)
    }
  }
}
