@file:OptIn(ExperimentalMosaicInstrumentation::class)

package org.buildmosaic.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.injection.create
import org.buildmosaic.core.instrumentation.ExecutionOutcome
import org.buildmosaic.core.instrumentation.ExperimentalMosaicInstrumentation
import org.buildmosaic.core.instrumentation.MosaicInstrumentation
import org.buildmosaic.core.instrumentation.ProducerReference
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Suppress("LargeClass", "TooManyFunctions", "FunctionMaxLength")
class SingleTileProvenanceTest {
  @Test fun inlineNestedExecutionRestoresCallerContext() =
    runTest {
      val recording = RecordingInstrumentation()
      val external = RecordingInstrumentation.Identity(0, "external")
      recording.current.set(external)
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, Dispatchers.Unconfined)
      try {
        val leaf by singleTile { 7 }
        val parent by singleTile {
          val value = compose(leaf)
          assertSame(recording.execution("parent").identity, recording.current.get())
          value
        }
        assertEquals(7, mosaic.compose(parent))
        assertSame(external, recording.current.get())
        assertSame(recording.execution("parent").identity, recording.execution("leaf").caller!!.owner)
        assertSame(external, recording.execution("parent").caller!!.ambient)
        recording.assertCompletedOnce()
      } finally {
        recording.current.remove()
        mosaic.cancel()
      }
    }

  @Test fun ordinaryFactoryReferencesStayUnambiguous() =
    runTest {
      val construct = ::MosaicImpl
      val create = Canvas::create
      val ordinary = construct(emptyCanvas, StandardTestDispatcher(testScheduler))
      val defaults = MosaicImpl(emptyCanvas)
      val factory = create(emptyCanvas) as MosaicImpl
      val tile = singleTile { 7 }
      val mosaics = listOf(ordinary, defaults, factory)
      val results = mosaics.map { it.composeAsync(tile) }
      testScheduler.runCurrent()
      results.forEach { assertEquals(7, it.await()) }
      mosaics.forEach { it.cancel() }
    }

  @Test fun firstExecutionCapturesCallerAndPublishesOneIdentity() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording)
      val external = RecordingInstrumentation.Identity(0, "external")
      recording.current.set(external)
      val tile by singleTile { 42 }
      val result = mosaic.composeAsync(tile)
      recording.current.remove()
      assertEquals(42, result.await())
      // Default dispatcher may complete the value immediately before the completion notification.
      (mosaic as MosaicImpl).coroutineContext[kotlinx.coroutines.Job]!!.children.toList().forEach { it.join() }
      val execution = recording.execution("tile")
      assertNull(execution.caller!!.owner)
      assertSame(external, execution.caller.ambient)
      assertEquals(1, recording.captures.get())
      assertEquals(ExecutionOutcome.SUCCESS, execution.completions.single().outcome)
      assertEquals(0, execution.dependencies.size)
      recording.assertCompletedOnce()
    }

  @Test fun inFlightAndCompletedReuseRetainProducer() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val gate = CompletableDeferred<Unit>()
      val shared by singleTile {
        gate.await()
        7
      }
      val first by singleTile { compose(shared) }
      val second by singleTile { compose(shared) }
      val a = mosaic.composeAsync(first)
      val b = mosaic.composeAsync(second)
      testScheduler.runCurrent()
      val dependency = recording.execution("second").dependencies.single()
      assertSame(
        recording.execution("shared").identity,
        assertIs<ProducerReference.Published>(dependency.resolution).identity,
      )
      assertFalse(a.isCompleted)
      assertFalse(b.isCompleted)
      gate.complete(Unit)
      testScheduler.runCurrent()
      val later by singleTile { compose(shared) }
      val c = mosaic.composeAsync(later)
      testScheduler.runCurrent()
      assertEquals(7, c.await())
      assertSame(dependency, recording.execution("later").dependencies.single())
      assertEquals(1, recording.executions.count { it.identity.name == "shared" })
      // Third-party cached callers do not capture context or receive dependency callbacks.
      val captures = recording.captures.get()
      assertEquals(7, mosaic.compose(shared))
      assertEquals(captures, recording.captures.get())
      recording.assertCompletedOnce()
    }

  @Test fun callerCompletesBeforeProducerPublication() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val shared by singleTile { 7 }
      val caller by singleTile {
        composeAsync(shared)
        composeAsync(shared)
        42
      }
      var actualCompletion: Long? = null
      recording.callback = { phase ->
        if (phase == "complete" && recording.executions.size == 1) {
          val execution = recording.execution("caller")
          assertNull(execution.dependencies.single().resolution)
          assertFalse(execution.finalized.get())
          actualCompletion = execution.completions.single().completedAtNanos
        }
      }
      val result = mosaic.composeAsync(caller)
      testScheduler.runCurrent()
      assertEquals(42, result.await())
      val execution = recording.execution("caller")
      assertTrue(execution.finalized.get())
      assertEquals(actualCompletion, execution.completions.single().completedAtNanos)
      assertTrue(execution.finalizedAt.get()!! >= actualCompletion!!)
      assertSame(
        recording.execution("shared").identity,
        assertIs<ProducerReference.Published>(execution.dependencies.single().resolution).identity,
      )
      recording.assertCompletedOnce()
    }

  @Test fun nestedCompositionCarriesOwnedCallerAcrossSuspension() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val leaf by singleTile { 7 }
      val middle by singleTile {
        kotlinx.coroutines.yield()
        compose(leaf)
      }
      val root by singleTile { compose(middle) }
      val result = mosaic.composeAsync(root)
      testScheduler.runCurrent()
      assertEquals(7, result.await())
      assertSame(recording.execution("root").identity, recording.execution("middle").caller!!.owner)
      assertSame(recording.execution("middle").identity, recording.execution("leaf").caller!!.owner)
      assertSame(recording.execution("middle").identity, recording.execution("leaf").caller!!.ambient)
      recording.assertCompletedOnce()
    }

  @Test fun thrownFailureRetainsOriginalExceptionAndCompletion() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val failure = IllegalArgumentException("application")
      val tile by singleTile<Int> { throw failure }
      val result = mosaic.composeAsync(tile)
      testScheduler.runCurrent()
      assertApplicationFailure(failure, assertFailsWith<IllegalArgumentException> { result.await() })
      assertSame(result, mosaic.composeAsync(tile))
      val completion = recording.execution("tile").completions.single()
      assertEquals(failure.javaClass.name, completion.errorType)
      assertEquals(ExecutionOutcome.FAILURE, completion.outcome)
    }

  @Test fun cancellingAwaiterDoesNotCancelSharedExecution() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val gate = CompletableDeferred<Unit>()
      val tile by singleTile {
        gate.await()
        7
      }
      val caller = async(start = CoroutineStart.UNDISPATCHED) { mosaic.compose(tile) }
      testScheduler.runCurrent()
      caller.cancel()
      caller.join()
      assertTrue(recording.execution("tile").completions.isEmpty())
      gate.complete(Unit)
      testScheduler.runCurrent()
      assertEquals(7, mosaic.compose(tile))
      assertEquals(ExecutionOutcome.SUCCESS, recording.execution("tile").completions.single().outcome)
    }

  @Test fun scopeCancellationCompletesStartedExecutionOnce() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val tile by singleTile<Int> { awaitCancellation() }
      val result = mosaic.composeAsync(tile)
      testScheduler.runCurrent()
      mosaic.cancel()
      testScheduler.runCurrent()
      assertFailsWith<CancellationException> { result.await() }
      assertEquals(ExecutionOutcome.CANCELLED, recording.execution("tile").completions.single().outcome)
      recording.assertCompletedOnce()
    }

  @Test fun ownedCallerCancellationLeavesProducerRunning() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val gate = CompletableDeferred<Unit>()
      val shared by singleTile {
        gate.await()
        7
      }
      val caller by singleTile<Int> {
        composeAsync(shared)
        composeAsync(shared)
        currentCoroutineContext().cancel()
        awaitCancellation()
      }
      val result = mosaic.composeAsync(caller)
      testScheduler.runCurrent()
      assertFailsWith<CancellationException> { result.await() }
      assertEquals(ExecutionOutcome.CANCELLED, recording.execution("caller").completions.single().outcome)
      assertTrue(recording.execution("shared").completions.isEmpty())
      gate.complete(Unit)
      testScheduler.runCurrent()
      assertEquals(7, mosaic.compose(shared))
      assertEquals(ExecutionOutcome.SUCCESS, recording.execution("shared").completions.single().outcome)
      recording.assertCompletedOnce()
    }

  @Test fun manyUnresolvedReuseReportsCanBeBounded() =
    runTest {
      val recording = RecordingInstrumentation(limit = 4)
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val shared by singleTile { 7 }
      val caller by singleTile {
        composeAsync(shared)
        repeat(10_000) { composeAsync(shared) }
        42
      }
      val result = mosaic.composeAsync(caller)
      testScheduler.runCurrent()
      assertEquals(42, result.await())
      val execution = recording.execution("caller")
      assertEquals(10_000, execution.reported.get())
      assertEquals(4, execution.dependencies.size)
      assertTrue(execution.finalized.get())
      recording.assertCompletedOnce()
    }

  @Test fun declinedObservationAbandonsReferenceWithoutFakeExecution() =
    runTest {
      val recording = RecordingInstrumentation()
      val provider =
        object : MosaicInstrumentation by recording {
          override fun startSingle(
            name: String?,
            caller: MosaicInstrumentation.CallerContext?,
          ): MosaicInstrumentation.Execution? = if (name == "shared") null else recording.startSingle(name, caller)
        }
      val mosaic = MosaicImpl.instrumented(emptyCanvas, provider, StandardTestDispatcher(testScheduler))
      val shared by singleTile { 7 }
      val caller by singleTile {
        composeAsync(shared)
        compose(shared)
      }
      val result = mosaic.composeAsync(caller)
      testScheduler.runCurrent()
      assertEquals(7, result.await())
      assertEquals(1, recording.executions.size)
      assertSame(ProducerReference.Abandoned, recording.execution("caller").dependencies.single().resolution)
      recording.assertCompletedOnce()
    }

  @Test fun cancellationBeforeStartAbandonsVisibleProducer() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val shared by singleTile { error("must not start") }
      val caller by singleTile {
        composeAsync(shared)
        composeAsync(shared)
        mosaic.cancel()
        7
      }
      mosaic.composeAsync(caller)
      testScheduler.runCurrent()
      assertEquals(1, recording.executions.size)
      val execution = recording.execution("caller")
      assertSame(ProducerReference.Abandoned, execution.dependencies.single().resolution)
      assertTrue(execution.finalized.get())
      assertTrue(mosaic.composeAsync(shared).isCancelled)
      recording.assertCompletedOnce()
    }

  @Test fun manyConcurrentCallersShareOneExecutionAndBoundDependencies() =
    runBlocking {
      val recording = RecordingInstrumentation(limit = 4)
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording)
      val starts = AtomicInteger()
      val gate = CompletableDeferred<Unit>()
      val shared by singleTile {
        starts.incrementAndGet()
        gate.await()
        7
      }
      val callers = List(128) { singleTile { compose(shared) } }
      withTimeout(10_000) {
        val pending = callers.map { async { mosaic.compose(it) } }
        gate.complete(Unit)
        assertTrue(pending.awaitAll().all { it == 7 })
        mosaic.coroutineContext[kotlinx.coroutines.Job]!!.children.toList().forEach { it.join() }
      }
      assertEquals(1, starts.get())
      assertEquals(129, recording.executions.size)
      assertEquals(127, recording.executions.sumOf { it.reported.get() })
      val producer = recording.execution("shared").identity
      recording.executions.flatMap { it.dependencies }.forEach {
        assertSame(producer, assertIs<ProducerReference.Published>(it.resolution).identity)
      }
      recording.assertCompletedOnce()
      mosaic.cancel()
    }
}
