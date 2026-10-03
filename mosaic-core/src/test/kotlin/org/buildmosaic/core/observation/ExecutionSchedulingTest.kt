package org.buildmosaic.core.observation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.buildmosaic.core.MosaicImpl
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.multiTile
import org.buildmosaic.core.singleTile
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Suppress("LargeClass") // Verify startup ownership and shared consumption through the runtime interface.
class ExecutionSchedulingTest {
  @Test fun cancelBeforeDispatchSettlesAcceptedWork() =
    runTest {
      for (observed in listOf(false, true)) {
        val observer = RecordingObserver()
        val mosaic =
          MosaicImpl(
            canvas { if (observed) installExecutionObserver { observer } },
            StandardTestDispatcher(testScheduler),
          )
        val single = mosaic.composeAsync(singleTile { error("cancelled work must not run") })
        val multi = mosaic.composeAsync(multiTile<Int, Int> { error("cancelled work must not run") }, listOf(1, 2))
        val completions = AtomicInteger()
        (multi.values + single).forEach { it.invokeOnCompletion { completions.incrementAndGet() } }
        mosaic.cancel()
        testScheduler.runCurrent()
        val request = checkNotNull(mosaic.coroutineContext[Job])
        request.join()
        assertTrue(request.isCompleted)
        assertTrue(request.children.none())
        assertTrue(single.isCancelled)
        assertTrue(multi.values.all { it.isCancelled })
        assertEquals(3, completions.get())
        assertTrue(observer.executions.isEmpty())
      }
    }

  @Test fun cancelBeforeChildDispatchAbandonsOnce() =
    runTest {
      for (kind in ExecutionKind.entries) {
        val observer = RecordingObserver()
        val mosaic = MosaicImpl(canvas { installExecutionObserver { observer } }, StandardTestDispatcher(testScheduler))
        val notifications = AtomicInteger()
        val completions = AtomicInteger()
        observer.dependencyHook = {
          observer.executions.single().dependencies.single().subscribe { notifications.incrementAndGet() }
        }
        observer.completionHook = { completions.incrementAndGet() }
        val parent =
          mosaic.composeAsync(
            singleTile {
              when (kind) {
                ExecutionKind.SINGLE -> composeAsync(singleTile { error("must not run") })
                ExecutionKind.MULTI -> composeAsync(multiTile<Int, Int> { error("must not run") }, 1)
              }
              mosaic.cancel()
              1
            },
          )
        testScheduler.runCurrent()
        val request = checkNotNull(mosaic.coroutineContext[Job])
        request.join()
        assertTrue(parent.isCancelled)
        assertTrue(request.children.none())
        val execution = observer.executions.single()
        val producer = execution.dependencies.single()
        assertEquals(
          UnavailableReason.NEVER_STARTED,
          assertIs<ProducerResolution.Unavailable>(producer.resolution).reason,
        )
        assertEquals(ExecutionOutcome.CANCELLATION, execution.completion?.outcome)
        assertEquals(1, notifications.get())
        assertEquals(1, completions.get())
      }
    }

  @Test fun executorRejectionSettlesStartup() =
    runBlocking {
      for (kind in ExecutionKind.entries) {
        val rejected = AtomicBoolean()
        val dispatcher =
          Executor { command ->
            if (rejected.get()) throw RejectedExecutionException("executor stopped")
            command.run()
          }.asCoroutineDispatcher()
        val observer = RecordingObserver()
        val mosaic = MosaicImpl(canvas { installExecutionObserver { observer } }, dispatcher)
        val notifications = AtomicInteger()
        val completions = AtomicInteger()
        observer.completionHook = { completions.incrementAndGet() }
        try {
          val parent =
            mosaic.composeAsync(
              singleTile {
                rejected.set(true)
                val child =
                  when (kind) {
                    ExecutionKind.SINGLE -> composeAsync(singleTile { error("rejected work must not run") })
                    ExecutionKind.MULTI -> composeAsync(multiTile<Int, Int> { error("rejected work must not run") }, 1)
                  }
                child.invokeOnCompletion { notifications.incrementAndGet() }
                child
              },
            )
          val child = withTimeout(5_000) { parent.await() }
          val request = checkNotNull(mosaic.coroutineContext[Job])
          withTimeout(5_000) { while (request.children.any()) request.children.toList().forEach { it.join() } }
          assertTrue(child.isCancelled)
          assertEquals(1, notifications.get())
          assertEquals(1, completions.get())
          val producer = observer.executions.single().dependencies.single()
          val resolutions = AtomicInteger()
          producer.subscribe { resolutions.incrementAndGet() }
          assertEquals(
            UnavailableReason.NEVER_STARTED,
            assertIs<ProducerResolution.Unavailable>(producer.resolution).reason,
          )
          assertEquals(1, resolutions.get())
          assertTrue(request.isActive)
          rejected.set(false)
          assertEquals(2, mosaic.compose(singleTile { 2 }))
        } finally {
          mosaic.cancel()
        }
      }
    }

  @Test fun cancelledWaiterPreservesSharedWork() =
    runTest {
      for (observed in listOf(false, true)) {
        for (kind in ExecutionKind.entries) {
          val observer = RecordingObserver()
          val mosaic =
            MosaicImpl(
              canvas { if (observed) installExecutionObserver { observer } },
              StandardTestDispatcher(testScheduler),
            )
          val gate = CompletableDeferred<Unit>()
          var invocations = 0
          val single =
            singleTile {
              invocations++
              gate.await()
              4
            }
          val multi =
            multiTile<Int, Int> { keys ->
              invocations++
              gate.await()
              keys.associateWith { 4 }
            }
          val shared =
            when (kind) {
              ExecutionKind.SINGLE -> mosaic.composeAsync(single)
              ExecutionKind.MULTI -> mosaic.composeAsync(multi, 1)
            }
          val departing = launch { shared.await() }
          val remaining = async { shared.await() }
          testScheduler.runCurrent()
          departing.cancel()
          testScheduler.runCurrent()
          assertFalse(shared.isCancelled)
          assertFalse(remaining.isCompleted)
          gate.complete(Unit)
          testScheduler.runCurrent()
          assertEquals(4, remaining.await())
          val cached =
            when (kind) {
              ExecutionKind.SINGLE -> mosaic.composeAsync(single)
              ExecutionKind.MULTI -> mosaic.composeAsync(multi, 1)
            }
          assertSame(shared, cached)
          assertEquals(1, invocations)
          if (observed) assertEquals(ExecutionOutcome.SUCCESS, observer.executions.single().completion?.outcome)
          assertTrue(checkNotNull(mosaic.coroutineContext[Job]).children.none())
        }
      }
    }

  @Test fun manualChildrenDrainAndCancelWithRequest() =
    runTest {
      for (kind in ExecutionKind.entries) {
        for (cancelRequest in listOf(false, true)) {
          val mosaic = MosaicImpl(canvas {}, StandardTestDispatcher(testScheduler))
          val gate = CompletableDeferred<Unit>()
          var childSettled = false
          val work: suspend () -> Int = {
            CoroutineScope(currentCoroutineContext()).launch {
              try {
                gate.await()
              } finally {
                childSettled = true
              }
            }
            3
          }
          val result =
            when (kind) {
              ExecutionKind.SINGLE -> mosaic.composeAsync(singleTile { work() })
              ExecutionKind.MULTI ->
                mosaic.composeAsync(
                  multiTile<Int, Int> {
                      keys ->
                    keys.associateWith { work() }
                  },
                  1,
                )
            }
          testScheduler.runCurrent()
          assertEquals(3, result.await())
          val request = checkNotNull(mosaic.coroutineContext[Job]) as CompletableJob
          request.complete()
          testScheduler.runCurrent()
          assertFalse(request.isCompleted)
          assertFalse(childSettled)
          if (cancelRequest) request.cancel() else gate.complete(Unit)
          testScheduler.runCurrent()
          request.join()
          assertTrue(childSettled)
          assertTrue(request.isCompleted)
          assertTrue(request.children.none())
          assertEquals(3, result.await())
        }
      }
    }

  @Test fun explicitScopeContainsChildFailure() =
    runTest {
      for (kind in ExecutionKind.entries) {
        val mosaic = MosaicImpl(canvas {}, StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        val work: suspend () -> Int = {
          coroutineScope {
            launch {
              gate.await()
              error("scoped child failed")
            }
            3
          }
        }
        val result =
          when (kind) {
            ExecutionKind.SINGLE -> mosaic.composeAsync(singleTile { work() })
            ExecutionKind.MULTI -> mosaic.composeAsync(multiTile<Int, Int> { keys -> keys.associateWith { work() } }, 1)
          }
        testScheduler.runCurrent()
        assertFalse(result.isCompleted)
        gate.complete(Unit)
        testScheduler.runCurrent()
        assertEquals("scoped child failed", assertFailsWith<IllegalStateException> { result.await() }.message)
        val request = checkNotNull(mosaic.coroutineContext[Job])
        assertTrue(request.children.none())
        assertTrue(request.isActive)
        assertEquals(4, mosaic.compose(singleTile { 4 }))
        mosaic.cancel()
      }
    }
}
