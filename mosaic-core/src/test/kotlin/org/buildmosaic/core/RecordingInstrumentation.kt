@file:OptIn(ExperimentalMosaicInstrumentation::class)

package org.buildmosaic.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.injection.CanvasKey
import org.buildmosaic.core.instrumentation.ExecutionCompletion
import org.buildmosaic.core.instrumentation.ExperimentalMosaicInstrumentation
import org.buildmosaic.core.instrumentation.MosaicInstrumentation
import org.buildmosaic.core.instrumentation.ProducerReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

internal val emptyCanvas =
  object : Canvas {
    override fun <T : Any> sourceOr(key: CanvasKey<T>): T? = null
  }

// Coroutine debug stack recovery may copy an exception with the original as its cause at await.
internal fun assertApplicationFailure(
  expected: Throwable,
  observed: Throwable,
) {
  assertTrue(observed === expected || observed.cause === expected, "Application exception was replaced: $observed")
}

internal class RecordingInstrumentation(
  private val limit: Int = Int.MAX_VALUE,
  private val recordBoundary: Boolean = false,
) : MosaicInstrumentation {
  data class Identity(val number: Int, val name: String?) : MosaicInstrumentation.ExecutionIdentity

  class Caller(val owner: Identity?, val ambient: Identity?) : MosaicInstrumentation.CallerContext

  val current = ThreadLocal<Identity?>()
  val executions = ConcurrentLinkedQueue<RecordedExecution>()
  val batches = ConcurrentLinkedQueue<RecordedBatch>()
  val failures = ConcurrentLinkedQueue<Throwable>()
  val receivedArguments = ConcurrentLinkedQueue<Any>()
  val captures = AtomicInteger()
  val next = AtomicInteger()
  var callback: (String) -> Unit = {}
  var context: ((RecordedExecution) -> CoroutineContext)? = null

  override fun captureCaller(execution: MosaicInstrumentation.ExecutionIdentity?): Caller {
    recordArguments(execution)
    callback("capture")
    captures.incrementAndGet()
    return Caller(execution as Identity?, current.get())
  }

  override fun startSingle(
    name: String?,
    caller: MosaicInstrumentation.CallerContext?,
  ): RecordedExecution {
    recordArguments(name, caller)
    callback("single")
    return RecordedExecution(name, caller as Caller?, null).also { executions.add(it) }
  }

  override fun createBatch(): RecordedBatch {
    callback("batch")
    return RecordedBatch().also { batches.add(it) }
  }

  override fun onCallbackFailure(failure: Throwable) {
    failures.add(failure)
    callback("diagnostic")
  }

  inner class RecordedBatch : MosaicInstrumentation.Batch {
    val callers = ArrayList<Caller?>()
    var contributed = 0
    var frozen = false
    var abandoned = false
    var abandonCalls = 0

    override fun contribute(caller: MosaicInstrumentation.CallerContext?) {
      recordArguments(caller)
      callback("contribute")
      check(!frozen)
      contributed++
      if (callers.size < limit) callers.add(caller as Caller?)
    }

    override fun start(
      name: String?,
      batchSize: Int,
    ): RecordedExecution {
      recordArguments(name, batchSize)
      callback("batchStart")
      check(!frozen)
      frozen = true
      return RecordedExecution(name, null, this, batchSize).also { executions.add(it) }
    }

    override fun abandon() {
      abandonCalls++
      callback("abandon")
      check(!abandoned)
      abandoned = true
      frozen = true
      callers.clear()
    }
  }

  inner class RecordedExecution(
    name: String?,
    val caller: Caller?,
    val batch: RecordedBatch?,
    val batchSize: Int? = null,
  ) : MosaicInstrumentation.Execution {
    private val token = Identity(next.incrementAndGet(), name)
    override val identity: Identity get() {
      callback("identity")
      return token
    }
    override val coroutineContext: CoroutineContext get() {
      callback("context")
      return context?.invoke(this) ?: current.asContextElement(token)
    }
    val dependencies = ConcurrentLinkedQueue<ProducerReference>()
    val retained = AtomicInteger()
    val completions = ConcurrentLinkedQueue<ExecutionCompletion>()
    val finalized = AtomicBoolean()
    val finalizedAt = AtomicReference<Long?>()
    val reported = AtomicInteger()

    override fun dependency(producer: ProducerReference) {
      recordArguments(producer)
      callback("dependency")
      reported.incrementAndGet()
      if (retained.getAndIncrement() < limit) {
        dependencies.add(producer)
        producer.subscribe { resolution ->
          recordArguments(resolution)
          callback("subscriber")
          finalizeIfReady()
        }
      }
    }

    override fun complete(completion: ExecutionCompletion) {
      recordArguments(completion)
      assertNotSame(token, current.get(), "Completed execution context must be removed")
      completions.add(completion)
      callback("complete")
      finalizeIfReady()
    }

    private fun finalizeIfReady() {
      val resolved = completions.isNotEmpty() && dependencies.all { it.resolution != null }
      if (resolved && finalized.compareAndSet(false, true)) {
        finalizedAt.set(System.nanoTime())
      }
    }
  }

  fun execution(name: String): RecordedExecution = executions.single { it.identity.name == name }

  private fun recordArguments(vararg arguments: Any?) {
    if (recordBoundary) arguments.filterNotNull().forEach { receivedArguments.add(it) }
  }

  fun assertCompletedOnce(allowCallbackFailures: Boolean = false) {
    executions.forEach { assertEquals(1, it.completions.size) }
    if (!allowCallbackFailures) assertTrue(failures.isEmpty(), failures.toString())
  }
}

/** Existing runtime scenarios are inherited unchanged by instrumented variants. */
@OptIn(ExperimentalMosaicInstrumentation::class)
open class RuntimeBehaviorTest {
  protected open val instrumentation: MosaicInstrumentation? = null

  protected fun createMosaic(
    canvas: Canvas,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
  ): MosaicImpl =
    instrumentation?.let {
      MosaicImpl.instrumented(canvas, it, dispatcher)
    } ?: MosaicImpl(canvas, dispatcher)
}

@OptIn(ExperimentalMosaicInstrumentation::class)
class InstrumentedMosaicBehaviorTest : MosaicTest() {
  override val instrumentation: MosaicInstrumentation = RecordingInstrumentation()
}

@OptIn(ExperimentalMosaicInstrumentation::class)
class InstrumentedConcurrencyBehaviorTest : MosaicConcurrencyTest() {
  override val instrumentation: MosaicInstrumentation = RecordingInstrumentation()
}

@OptIn(ExperimentalMosaicInstrumentation::class)
class InstrumentedCoalescingBehaviorTest : MultiTileCoalescingTest() {
  override val instrumentation: MosaicInstrumentation = RecordingInstrumentation()
}

@OptIn(ExperimentalMosaicInstrumentation::class)
class InstrumentedDslBehaviorTest : TileDslTest() {
  override val instrumentation: MosaicInstrumentation = RecordingInstrumentation()
}
