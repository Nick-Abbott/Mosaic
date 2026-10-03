package org.buildmosaic.core.observation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.buildmosaic.core.MosaicImpl
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.multiTile
import org.buildmosaic.core.singleTile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Suppress("LargeClass") // Keep coroutine propagation and restoration scenarios together.
class ExecutionContextTest {
  @Test fun ownedChildrenKeepObservationAfterResult() =
    runTest {
      for (kind in ExecutionKind.entries) {
        val observer = RecordingObserver()
        val mosaic = MosaicImpl(canvas { installExecutionObserver { observer } }, StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        var childContext: ExecutionIdentity? = null
        val work: suspend () -> Int = {
          CoroutineScope(currentCoroutineContext()).launch {
            gate.await()
            childContext = observer.ambient.get()
            mosaic.compose(singleTile { 1 })
            error("owned child failed after publication")
          }
          42
        }
        val result =
          when (kind) {
            ExecutionKind.SINGLE -> mosaic.composeAsync(singleTile { work() })
            ExecutionKind.MULTI -> mosaic.composeAsync(multiTile<Int, Int> { keys -> keys.associateWith { work() } }, 1)
          }
        result.invokeOnCompletion { mosaic.composeAsync(singleTile { 2 }) }
        testScheduler.runCurrent()
        assertEquals(42, result.await())
        val execution = observer.executions.first()
        assertNull(execution.completion)
        assertNull(observer.executions[1].start.contributors.initiating.execution)
        val releasedAt = System.nanoTime()
        gate.complete(Unit)
        testScheduler.runCurrent()
        assertSame(execution.identity, childContext)
        assertSame(execution.identity, observer.executions[2].start.contributors.initiating.execution)
        assertEquals(1, execution.dependencies.size)
        val completion = checkNotNull(execution.completion)
        assertEquals(ExecutionOutcome.FAILURE, completion.outcome)
        assertEquals(IllegalStateException::class.java.name, completion.exceptionClassName)
        assertTrue(completion.completedAtNanos >= releasedAt)
        assertEquals(42, result.await())
        assertTrue(observer.failures.isEmpty())
      }
    }

  @Test fun nestedAndParallelWorkKeepContext() =
    runBlocking {
      val observer = RecordingObserver()
      val mosaic = MosaicImpl(canvas { installExecutionObserver { observer } }, Dispatchers.Unconfined)
      val parent =
        singleTile {
          val parentIdentity = observer.ambient.get()
          assertSame(observer.executions.first().identity, parentIdentity)
          val child =
            singleTile {
              val own = observer.ambient.get()
              assertTrue(own !== parentIdentity)
              withContext(Dispatchers.Default) {
                delay(1)
                assertSame(own, observer.ambient.get())
                coroutineScope {
                  val a =
                    async {
                      delay(1)
                      assertSame(own, observer.ambient.get())
                    }
                  val b =
                    async {
                      delay(1)
                      assertSame(own, observer.ambient.get())
                    }
                  a.await()
                  b.await()
                }
              }
              assertSame(own, observer.ambient.get())
              3
            }
          assertEquals(3, compose(child))
          assertSame(parentIdentity, observer.ambient.get())
          compose(singleTile { 4 })
        }
      assertEquals(4, mosaic.compose(parent))
      assertNull(observer.ambient.get())
      observer.executions.drop(1).forEach {
        assertSame(observer.executions.first().identity, it.start.contributors.initiating.execution)
      }
    }

  @Suppress("LongMethod") // Keep both threads and their restoration choreography visible in one test.
  @Test
  fun overlappingUpdatesUsePerInstallationTokens() {
    val ambient = ThreadLocal<String?>()
    val context =
      ExecutionContext {
        val previous = ambient.get()
        ambient.set("installed")
        AutoCloseable { ambient.set(previous) }
      }
    val execution =
      ObservedExecution(
        ObservationCalls(RecordingObserver()),
        StartedObservation(
          object : ExecutionObservation {
            override fun onComplete(completion: ExecutionCompletion) {
              error("No execution completion in context interleaving test")
            }
          },
          TestIdentity(1),
          context,
        ),
      )

    @Suppress("UNCHECKED_CAST")
    val element =
      execution.context.fold<ThreadContextElement<Any?>?>(null) { _, item ->
        item as ThreadContextElement<Any?>
      }
    val firstUpdated = CountDownLatch(1)
    val secondUpdated = CountDownLatch(1)
    val failure = AtomicReference<Throwable?>()
    val one =
      thread {
        runCatching {
          ambient.set("first")
          val token = checkNotNull(element).updateThreadContext(EmptyCoroutineContext)
          firstUpdated.countDown()
          check(secondUpdated.await(5, TimeUnit.SECONDS))
          element.restoreThreadContext(EmptyCoroutineContext, token)
          assertEquals("first", ambient.get())
          assertNull(ObservedExecution.current())
        }.onFailure { failure.set(it) }
      }
    val two =
      thread {
        runCatching {
          check(firstUpdated.await(5, TimeUnit.SECONDS))
          ambient.set("second")
          val token = checkNotNull(element).updateThreadContext(EmptyCoroutineContext)
          // Nested update on this thread must restore this installation, not thread one's token.
          val nested = element.updateThreadContext(EmptyCoroutineContext)
          element.restoreThreadContext(EmptyCoroutineContext, nested)
          assertEquals("installed", ambient.get())
          secondUpdated.countDown()
          element.restoreThreadContext(EmptyCoroutineContext, token)
          assertEquals("second", ambient.get())
          assertNull(ObservedExecution.current())
        }.onFailure {
          failure.set(it)
          secondUpdated.countDown()
        }
      }
    one.join(5_000)
    two.join(5_000)
    assertFalse(one.isAlive)
    assertFalse(two.isAlive)
    assertNull(failure.get())
    execution.recordCompletion(null)
    ambient.set("after completion")
    val token = checkNotNull(element).updateThreadContext(EmptyCoroutineContext)
    assertEquals("after completion", ambient.get())
    assertNull(ObservedExecution.current())
    element.restoreThreadContext(EmptyCoroutineContext, token)
  }

  @Test fun installAndRestoreFailuresAreContained() =
    runTest {
      val observer = RecordingObserver()
      val ambient = ThreadLocal<String?>()
      ambient.set("caller")
      observer.context =
        ExecutionContext {
          ambient.set("partial")
          ambient.set("caller") // Provider must undo its own partial mutation before throwing.
          error("private install failure")
        }
      val mosaic = MosaicImpl(canvas { installExecutionObserver { observer } }, Dispatchers.Unconfined)
      assertEquals(
        1,
        mosaic.compose(
          singleTile {
            assertEquals("caller", ambient.get())
            1
          },
        ),
      )
      observer.context =
        ExecutionContext {
          val prior = ambient.get()
          ambient.set("execution")
          AutoCloseable {
            ambient.set(prior)
            error("private restore failure")
          }
        }
      assertEquals(
        2,
        mosaic.compose(
          singleTile {
            assertEquals("execution", ambient.get())
            2
          },
        ),
      )
      assertEquals("caller", ambient.get())
      assertTrue(observer.failures.any { it.operation == CallbackOperation.INSTALL })
      assertTrue(observer.failures.any { it.operation == CallbackOperation.RESTORE })
      assertNull(ObservedExecution.current())
    }
}
