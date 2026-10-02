package org.buildmosaic.core.injection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

@Suppress("FunctionMaxLength", "LargeClass")
class CanvasLifecycleTest {
  @Test
  fun `failure closes local values in creation order reversed including early paint`() =
    runTest {
      val closed = mutableListOf<String>()
      val parentResource = Resource { closed.add("parent") }
      val parent = canvas { single { parentResource } }
      val failure = IllegalArgumentException("failed construction")
      var paintedCount = 0
      val thrown =
        assertFailsWith<IllegalArgumentException> {
          parent.withLayer {
            single<Resource>("first") { Resource { closed.add("first") } }
            single<Resource>("consumer") {
              assertSame(parentResource, paint<Resource>())
              assertSame(paint<Resource>("painted"), paint<Resource>("painted"))
              Resource { closed.add("consumer") }
            }
            single<String> { throw failure }
            single<Resource>("painted") {
              paintedCount++
              Resource { closed.add("painted") }
            }
            single<Int> { error("must not be constructed") }
          }
        }

      assertSame(failure, thrown)
      assertEquals(1, paintedCount)
      assertEquals(listOf("consumer", "painted", "first"), closed)
      parent.close()
      assertEquals(listOf("consumer", "painted", "first", "parent"), closed)
    }

  @Test
  fun `cleanup failures are suppressed on original failure and do not stop cleanup`() =
    runTest {
      val closed = mutableListOf<String>()
      val failure = AssertionError("failed construction")
      val firstCleanup = IllegalStateException("first cleanup")
      val lastCleanup = IllegalArgumentException("last cleanup")
      val thrown =
        assertFailsWith<AssertionError> {
          canvas {
            single<Resource>("first") {
              Resource {
                closed.add("first")
                throw firstCleanup
              }
            }
            single<Resource>("self") {
              Resource {
                closed.add("self")
                throw failure
              }
            }
            single<Resource>("last") {
              Resource {
                closed.add("last")
                throw lastCleanup
              }
            }
            single<String> { throw failure }
          }
        }

      assertSame(failure, thrown)
      assertEquals(listOf(lastCleanup, firstCleanup), thrown.suppressed.toList())
      assertEquals(listOf("last", "self", "first"), closed)
    }

  @Test
  fun `cancellation during a suspended constructor cleans local values and preserves cancellation`() =
    runTest {
      val closed = mutableListOf<String>()
      val parent = canvas { single { Resource { closed.add("parent") } } }
      val entered = CompletableDeferred<Unit>()
      val cancellation = CancellationException("cancel construction")
      val cleanup = IllegalStateException("cleanup")
      var constructorFailure: CancellationException? = null
      var original: Throwable? = null
      val building =
        launch {
          try {
            parent.withLayer {
              single<Resource>("first") { Resource { closed.add("first") } }
              single<String> {
                paint<Resource>()
                paint<Resource>("painted")
                entered.complete(Unit)
                try {
                  awaitCancellation()
                } catch (failure: CancellationException) {
                  constructorFailure = failure
                  throw failure
                }
              }
              single<Resource>("painted") {
                Resource {
                  closed.add("painted")
                  throw cleanup
                }
              }
            }
          } catch (failure: CancellationException) {
            original = failure
            throw failure
          }
        }
      entered.await()
      building.cancel(cancellation)
      building.join()

      assertSame(constructorFailure, original)
      assertEquals(cancellation.message, original?.message)
      assertEquals(listOf(cleanup), original?.suppressed?.toList())
      assertEquals(listOf("painted", "first"), closed)
      parent.close()
      assertEquals(listOf("painted", "first", "parent"), closed)
    }

  @Test
  fun `cancellation cannot transfer ownership after a constructor returns`() =
    runTest {
      var closed = false
      val building =
        launch {
          canvas {
            single {
              currentCoroutineContext()[Job]!!.cancel()
              Resource { closed = true }
            }
          }
          error("cancelled construction must not return a Canvas")
        }
      building.join()
      assertEquals(true, building.isCancelled)
      assertEquals(true, closed)
    }

  @Test
  fun `successful use preserves binding close order and parent ownership`() =
    runTest {
      val events = mutableListOf<String>()
      val parent: Canvas = canvas { single { Resource { events.add("parent") } } }
      parent.withLayer {
        single<Resource>("consumer") {
          paint<Resource>()
          paint<Resource>("dependency")
          events.add("create consumer")
          Resource { events.add("close consumer") }
        }
        single<Resource>("dependency") {
          events.add("create dependency")
          Resource { events.add("close dependency") }
        }
      }.use {
        assertEquals(listOf("create dependency", "create consumer"), events)
      }
      assertEquals(listOf("create dependency", "create consumer", "close consumer", "close dependency"), events)
      parent.close()
      assertEquals("parent", events.last())
    }

  private class Resource(private val onClose: () -> Unit) : AutoCloseable {
    override fun close() = onClose()
  }
}
