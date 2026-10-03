package org.buildmosaic.core.observation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.buildmosaic.core.MosaicImpl
import org.buildmosaic.core.Tile
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.singleTile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Suppress("LargeClass", "TooManyFunctions")
class ExecutionObservationTest {
  @Test fun publicationPrecedesSameTileCaptureReentry() =
    runTest {
      val observer = RecordingObserver()
      val mosaic = MosaicImpl(canvas { installExecutionObserver { observer } }, StandardTestDispatcher(testScheduler))
      val tile = singleTile { "result" }
      var nested: Deferred<String>? = null
      observer.captureHook = { nested = mosaic.composeAsync(tile) }
      val result = mosaic.composeAsync(tile)
      assertSame(result, nested)
      assertEquals(1, observer.captures)
      testScheduler.runCurrent()
      assertEquals("result", result.await())
      assertEquals(1, observer.executions.size)
    }

  @Test fun raceLoserDoesNotCaptureOrExecute() =
    runBlocking {
      val observer = RecordingObserver()
      val mosaic = MosaicImpl(canvas { installExecutionObserver { observer } }, Dispatchers.Default)
      val entered = CountDownLatch(1)
      val release = CountDownLatch(1)
      observer.captureHook = {
        entered.countDown()
        check(release.await(5, TimeUnit.SECONDS))
      }
      val tile = singleTile { 7 }
      var winner: Deferred<Int>? = null
      val worker = thread { winner = mosaic.composeAsync(tile) }
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val loser = mosaic.composeAsync(tile)
        assertEquals(1, observer.captures)
        release.countDown()
        worker.join(5_000)
        assertFalse(worker.isAlive)
        assertSame(winner, loser)
        withTimeout(5_000) { assertEquals(7, loser.await()) }
      } finally {
        release.countDown()
        mosaic.cancel()
      }
    }

  @Test fun inFlightAndCompletedReuseKeepTheSameProducer() =
    runTest {
      val observer = RecordingObserver()
      val mosaic = MosaicImpl(canvas { installExecutionObserver { observer } }, StandardTestDispatcher(testScheduler))
      val gate = CompletableDeferred<Unit>()
      val shared =
        singleTile {
          gate.await()
          9
        }
      val a = mosaic.composeAsync(singleTile { compose(shared) })
      testScheduler.runCurrent()
      val b = mosaic.composeAsync(singleTile { compose(shared) })
      testScheduler.runCurrent()
      val first = observer.executions[0].dependencies.single()
      assertSame(first, observer.executions[2].dependencies.single())
      assertIs<ProducerResolution.Published>(first.resolution)
      gate.complete(Unit)
      testScheduler.runCurrent()
      assertEquals(9, a.await())
      assertEquals(9, b.await())
      val c = mosaic.composeAsync(singleTile { compose(shared) })
      testScheduler.runCurrent()
      assertEquals(9, c.await())
      assertSame(first, observer.executions.last().dependencies.single())
      assertEquals(4, observer.executions.size)
    }

  @Test fun unresolvedReuseDoesNotDelayAnIndependentResult() =
    runTest {
      val observer = RecordingObserver()
      val mosaic = MosaicImpl(canvas { installExecutionObserver { observer } }, Dispatchers.Unconfined)
      lateinit var reserved: Tile<Int>
      var independent: Deferred<Int>? = null
      observer.captureHook = {
        observer.captureHook = null
        independent =
          mosaic.composeAsync(
            singleTile {
              composeAsync(reserved)
              3
            },
          )
      }
      reserved = singleTile { 5 }
      var unresolvedAtCompletion = false
      observer.completionHook = {
        if (observer.executions.size == 1) {
          unresolvedAtCompletion = observer.executions.single().dependencies.single().resolution == null
        }
      }
      assertEquals(5, mosaic.compose(reserved))
      assertEquals(3, checkNotNull(independent).await())
      assertTrue(unresolvedAtCompletion)
      assertIs<ProducerResolution.Published>(observer.executions.first().dependencies.single().resolution)
    }

  @Test fun callbackReentryHasNoCallerButNestedExecutionDoes() =
    runTest {
      val observer = RecordingObserver()
      val mosaic = MosaicImpl(canvas { installExecutionObserver { observer } }, Dispatchers.Unconfined)
      val leaf = singleTile { 1 }
      val callbackWork = singleTile { compose(leaf) }
      observer.dependencyHook = {
        observer.dependencyHook = null
        mosaic.composeAsync(callbackWork)
      }
      val root = singleTile { compose(singleTile { 2 }) }
      assertEquals(2, mosaic.compose(root))
      val callback = observer.executions[2]
      val nested = observer.executions[3]
      assertNull(callback.start.contributors.initiating.execution)
      assertSame(callback.identity, nested.start.contributors.initiating.execution)
      assertEquals(1, callback.dependencies.size)
      assertEquals(1, observer.executions.first().dependencies.size)
    }

  @Test fun cancellationDuringCaptureDoesNotFabricateExecution() =
    runTest {
      val observer = RecordingObserver()
      val mosaic = MosaicImpl(canvas { installExecutionObserver { observer } }, StandardTestDispatcher(testScheduler))
      observer.captureHook = { mosaic.cancel() }
      val result = mosaic.composeAsync(singleTile { error("must not execute") })
      testScheduler.runCurrent()
      assertTrue(result.isCancelled)
      assertTrue(observer.executions.isEmpty())
    }

  @Test fun awaitingCallerCancellationLeavesSharedWorkOwned() =
    runTest {
      val observer = RecordingObserver()
      val mosaic = MosaicImpl(canvas { installExecutionObserver { observer } }, StandardTestDispatcher(testScheduler))
      val gate = CompletableDeferred<Unit>()
      val tile =
        singleTile {
          gate.await()
          4
        }
      val caller = launch { mosaic.compose(tile) }
      testScheduler.runCurrent()
      caller.cancel()
      gate.complete(Unit)
      testScheduler.runCurrent()
      assertEquals(4, mosaic.compose(tile))
      assertEquals(ExecutionOutcome.SUCCESS, observer.executions.single().completion?.outcome)
    }

  @Test fun applicationAndCallbackFailuresAreSeparate() =
    runTest {
      val observer = RecordingObserver()
      observer.captureHook = { error("private provider message") }
      observer.dependencyHook = { error("private dependency message") }
      observer.completionHook = { error("private completion message") }
      val mosaic = MosaicImpl(canvas { installExecutionObserver { observer } }, StandardTestDispatcher(testScheduler))
      val failure = object : IllegalArgumentException("private app message", RuntimeException("private cause")) {}
      failure.addSuppressed(IllegalStateException("private suppressed"))
      val result =
        mosaic.composeAsync(
          singleTile {
            compose(singleTile { 1 })
            throw failure
          },
        )
      testScheduler.runCurrent()
      assertSame(failure, assertFailsWith<IllegalArgumentException> { result.await() })
      val completion = checkNotNull(observer.executions.first().completion)
      assertEquals(ExecutionOutcome.FAILURE, completion.outcome)
      assertEquals(failure.javaClass.name, completion.exceptionClassName)
      assertTrue(
        observer.failures.map { it.operation }.containsAll(
          listOf(CallbackOperation.CAPTURE, CallbackOperation.DEPENDENCY, CallbackOperation.COMPLETE),
        ),
      )
      assertTrue(observer.failures.all { it.exceptionClassName == IllegalStateException::class.java.name })
      assertNull(observer.executions.first().start.contributors.initiating.context)
    }

  @Test fun startAndDiagnosticFailuresCannotReplaceResults() =
    runTest {
      val observer =
        object : ExecutionObserver {
          override fun captureCaller(): CallerContext? = null

          override fun onStart(start: ExecutionStart): StartedObservation = error("private start")

          override fun onCallbackFailure(failure: CallbackFailure) = error("private diagnostic")
        }
      val mosaic = MosaicImpl(canvas { installExecutionObserver { observer } }, StandardTestDispatcher(testScheduler))
      val result = mosaic.composeAsync(singleTile { 6 })
      testScheduler.runCurrent()
      assertEquals(6, result.await())
    }

  @Test fun optedOutInlineExecutionDoesNotBorrowCaller() =
    runTest {
      val recording = RecordingObserver()
      var starts = 0
      val observer =
        object : ExecutionObserver {
          override fun captureCaller(): CallerContext = recording.captureCaller()

          override fun onStart(start: ExecutionStart): StartedObservation? {
            starts++
            return if (starts == 2) null else recording.onStart(start)
          }
        }
      val mosaic = MosaicImpl(canvas { installExecutionObserver { observer } }, Dispatchers.Unconfined)
      val leaf = singleTile { 1 }
      val unobserved = singleTile { compose(leaf) }
      assertEquals(1, mosaic.compose(singleTile { compose(unobserved) }))
      assertNull(recording.executions[1].start.contributors.initiating.execution)
      assertEquals(1, recording.executions[0].dependencies.size)
      val producer = recording.executions[0].dependencies.single()
      assertEquals(UnavailableReason.NOT_OBSERVED, assertIs<ProducerResolution.Unavailable>(producer.resolution).reason)
    }

  @Test fun metricsObserverNeedsNeitherIdentityNorContext() =
    runTest {
      val completions = mutableListOf<ExecutionCompletion>()
      val dependencies = mutableListOf<ProducerReference>()
      val observer =
        object : ExecutionObserver {
          override fun captureCaller(): CallerContext? = null

          override fun onStart(start: ExecutionStart): StartedObservation =
            StartedObservation(
              object : ExecutionObservation {
                override fun onDependency(producer: ProducerReference) {
                  dependencies.add(producer)
                }

                override fun onComplete(completion: ExecutionCompletion) {
                  completions.add(completion)
                }
              },
            )
        }
      val mosaic = MosaicImpl(canvas { installExecutionObserver { observer } }, Dispatchers.Unconfined)
      assertEquals(1, mosaic.compose(singleTile { compose(singleTile { 1 }) }))
      assertEquals(2, completions.size)
      assertTrue(completions.all { it.outcome == ExecutionOutcome.SUCCESS })
      assertEquals(
        UnavailableReason.NOT_OBSERVED,
        assertIs<ProducerResolution.Unavailable>(dependencies.single().resolution).reason,
      )
    }
}
