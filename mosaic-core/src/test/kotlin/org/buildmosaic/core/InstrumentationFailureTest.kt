@file:OptIn(ExperimentalMosaicInstrumentation::class)

package org.buildmosaic.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.instrumentation.ExperimentalMosaicInstrumentation
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InstrumentationFailureTest {
  @Test fun callbackFailuresPreserveValuesCacheAndBatching() =
    runTest {
      val phases =
        listOf(
          "capture", "single", "identity", "context", "dependency", "batch",
          "contribute", "batchStart", "complete", "subscriber", "diagnostic",
        )
      for (broken in phases) {
        val recording = RecordingInstrumentation()
        val failure = CancellationException("adapter $broken")
        recording.callback = { phase ->
          if (phase == broken || (broken == "diagnostic" && phase == "capture")) throw failure
        }
        val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
        val shared by singleTile { 7 }
        val fetched = mutableListOf<Set<Int>>()
        val multi by multiTile<Int, Int> { keys ->
          fetched.add(keys)
          keys.associateWith { it }
        }
        val first by singleTile {
          composeAsync(shared)
          composeAsync(shared)
          composeAsync(multi, listOf(1, 2))
          composeAsync(multi, listOf(2, 3))
          42
        }
        val result = mosaic.composeAsync(first)
        testScheduler.runCurrent()
        assertEquals(42, result.await(), broken)
        assertEquals(7, mosaic.compose(shared), broken)
        assertEquals(mapOf(1 to 1, 2 to 2, 3 to 3), mosaic.compose(multi, listOf(1, 2, 3)), broken)
        assertEquals(listOf(setOf(1, 2, 3)), fetched, broken)
        assertSame(result, mosaic.composeAsync(first))
        assertTrue(recording.failures.isNotEmpty(), broken)
        assertTrue(recording.failures.all { it === failure }, broken)
        if (broken == "batchStart") assertEquals(1, recording.batches.single().abandonCalls)
        recording.assertCompletedOnce(allowCallbackFailures = true)
        mosaic.cancel()
      }
    }

  @Test fun callbackFailuresDoNotReplaceApplicationFailures() =
    runTest {
      val recording = RecordingInstrumentation()
      recording.callback = { if (it == "complete" || it == "diagnostic") error("adapter") }
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val failure = UnsupportedOperationException("application")
      val single by singleTile<Int> { throw failure }
      val multi by multiTile<Int, Int> { throw failure }
      val a = mosaic.composeAsync(single)
      val b = mosaic.composeAsync(multi, listOf(1, 2))
      testScheduler.runCurrent()
      assertApplicationFailure(failure, assertFailsWith<UnsupportedOperationException> { a.await() })
      b.values.forEach {
        assertApplicationFailure(
          failure,
          assertFailsWith<UnsupportedOperationException> { it.await() },
        )
      }
      recording.assertCompletedOnce(allowCallbackFailures = true)
    }

  @Test fun abandonmentFailureCannotLeakPendingWork() =
    runTest {
      val recording = RecordingInstrumentation()
      recording.callback = { if (it == "abandon" || it == "diagnostic") error("adapter") }
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val tile by multiTile<Int, Int> { error("must not start") }
      val result = mosaic.composeAsync(tile, listOf(1, 2))
      mosaic.cancel()
      testScheduler.runCurrent()
      result.values.forEach { assertFailsWith<CancellationException> { it.await() } }
      assertTrue(recording.executions.isEmpty())
      assertEquals(1, recording.failures.size)
    }

  @Test fun adapterContextCannotReplaceJobsOrDispatchers() =
    runTest {
      for (context in listOf(Job().apply { cancel() }, Dispatchers.Unconfined)) {
        val recording = RecordingInstrumentation()
        recording.context = { context }
        val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
        val tile by singleTile { 7 }
        val result = mosaic.composeAsync(tile)
        testScheduler.runCurrent()
        assertEquals(7, result.await())
        assertEquals(1, recording.failures.size)
        recording.assertCompletedOnce(allowCallbackFailures = true)
        mosaic.cancel()
      }
    }

  @Test fun threadContextFailuresAreContained() =
    runTest {
      for (updateFails in listOf(false, true)) {
        val recording = RecordingInstrumentation()
        val failure = IllegalArgumentException("adapter context")
        recording.context = { BrokenContext(updateFails, failure) }
        val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        val tile by singleTile {
          gate.await()
          7
        }
        val result = mosaic.composeAsync(tile)
        testScheduler.runCurrent()
        gate.complete(Unit)
        testScheduler.runCurrent()
        assertEquals(7, result.await())
        assertTrue(recording.failures.isNotEmpty())
        assertTrue(recording.failures.all { it === failure })
        recording.assertCompletedOnce(allowCallbackFailures = true)
        mosaic.cancel()
      }
    }

  private class BrokenContext(
    private val updateFails: Boolean,
    private val failure: Throwable,
  ) : ThreadContextElement<Unit> {
    companion object Key : CoroutineContext.Key<BrokenContext>

    override val key: CoroutineContext.Key<*> = Key

    override fun updateThreadContext(context: CoroutineContext) {
      if (updateFails) throw failure
    }

    override fun restoreThreadContext(
      context: CoroutineContext,
      oldState: Unit,
    ) {
      if (!updateFails) throw failure
    }
  }
}
