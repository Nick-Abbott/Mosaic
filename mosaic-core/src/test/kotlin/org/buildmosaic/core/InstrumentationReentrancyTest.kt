@file:OptIn(ExperimentalMosaicInstrumentation::class)

package org.buildmosaic.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.instrumentation.ExperimentalMosaicInstrumentation
import org.buildmosaic.core.instrumentation.MosaicInstrumentation
import org.buildmosaic.core.instrumentation.ProducerReference
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Suppress("LargeClass", "FunctionMaxLength")
class InstrumentationReentrancyTest {
  @Test fun createBatchReentryKeepsOneOwnerAndAccumulator() =
    runTest {
      for (dispatcher in listOf(StandardTestDispatcher(testScheduler), InlineDispatcher)) {
        val recording = RecordingInstrumentation()
        val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, dispatcher)
        val fetched = mutableListOf<Set<Int>>()
        val tile by multiTile<Int, Int> { keys ->
          fetched.add(keys)
          keys.associateWith { it }
        }
        var nested: Map<Int, Deferred<Int>>? = null
        recording.callback = { phase ->
          if (phase == "batch" && nested == null) {
            // Set the guard before synchronous provider reentry.
            nested = emptyMap()
            nested = mosaic.composeAsync(tile, listOf(1, 2))
          }
        }
        val first = mosaic.composeAsync(tile, 1)
        testScheduler.runCurrent()
        assertEquals(1, first.await())
        assertEquals(2, nested!!.getValue(2).await())
        assertEquals(listOf(setOf(1, 2)), fetched)
        assertEquals(1, recording.batches.size)
        assertEquals(2, recording.batches.single().contributed)
        assertEquals(0, recording.batches.single().abandonCalls)
        recording.assertCompletedOnce()
        mosaic.cancel()
      }
    }

  @Test fun createBatchReentryCanFinishANestedCallerBeforePublication() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, InlineDispatcher)
      val fetched = mutableListOf<Set<Int>>()
      val multi by multiTile<Int, Int> { keys ->
        fetched.add(keys)
        keys.associateWith { it }
      }
      val nested by singleTile {
        composeAsync(multi, 2)
        composeAsync(multi, 1)
        7
      }
      recording.callback = { phase ->
        if (phase == "batch") {
          assertTrue(mosaic.composeAsync(nested).isCompleted)
          assertEquals(1, recording.execution("nested").completions.size)
          assertFalse(recording.execution("nested").finalized.get())
        }
      }
      val caller by singleTile { compose(multi, 1) }
      assertEquals(1, mosaic.compose(caller))
      assertEquals(listOf(setOf(1, 2)), fetched)
      assertEquals(2, recording.execution("multi").batchSize)
      assertEquals(
        listOf(recording.execution("caller").identity, recording.execution("nested").identity),
        recording.batches.single().callers.map { it!!.owner },
      )
      val dependency = recording.execution("nested").dependencies.single()
      assertSame(
        recording.execution("multi").identity,
        assertIs<ProducerReference.Published>(dependency.resolution).identity,
      )
      assertTrue(recording.execution("nested").finalized.get())
      assertEquals(0, recording.batches.single().abandonCalls)
      recording.assertCompletedOnce()
    }

  @Test fun contributorReentryDoesNotOverlapCallbacks() =
    runTest {
      val recording = RecordingInstrumentation()
      lateinit var mosaic: MosaicImpl
      val tile by multiTile<Int, Int> { keys -> keys.associateWith { it } }
      var nested: Deferred<Int>? = null
      var reentered = false
      val provider =
        object : MosaicInstrumentation by recording {
          override fun createBatch(): MosaicInstrumentation.Batch {
            val batch = recording.createBatch()
            return object : MosaicInstrumentation.Batch by batch {
              private var contributing = false

              override fun contribute(caller: MosaicInstrumentation.CallerContext?) {
                check(!contributing) { "Concurrent or reentrant contributor callback" }
                contributing = true
                try {
                  if (!reentered) {
                    reentered = true
                    nested = mosaic.composeAsync(tile, 2)
                  }
                  batch.contribute(caller)
                } finally {
                  contributing = false
                }
              }
            }
          }
        }
      mosaic = MosaicImpl.instrumented(emptyCanvas, provider, InlineDispatcher)
      val first = mosaic.composeAsync(tile, 1)
      assertEquals(1, first.await())
      assertEquals(2, nested!!.await())
      assertEquals(1, recording.batches.size)
      assertEquals(2, recording.batches.single().contributed)
      recording.assertCompletedOnce()
    }

  @Test fun pendingHandoffWaitsForActiveContributor() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val fetched = mutableListOf<Set<Int>>()
      val tile by multiTile<Int, Int> { keys ->
        fetched.add(keys)
        keys.associateWith { it }
      }
      val first = mosaic.composeAsync(tile, 1)
      var nested: Deferred<Int>? = null
      var reentered = false
      recording.callback = { phase ->
        if (phase == "contribute" && !reentered) {
          reentered = true
          nested = mosaic.composeAsync(tile, 3)
          // Execute the previously scheduled owner while this callback is still active.
          testScheduler.runCurrent()
          assertTrue(recording.executions.isEmpty())
        }
      }
      val second = mosaic.composeAsync(tile, 2)
      testScheduler.runCurrent()
      assertEquals(1, first.await())
      assertEquals(2, second.await())
      assertEquals(3, nested!!.await())
      assertEquals(listOf(setOf(1, 2, 3)), fetched)
      assertEquals(3, recording.batches.single().contributed)
      recording.assertCompletedOnce()
    }

  @Test fun cancellationDuringContributionAbandonsOnce() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val tile by multiTile<Int, Int> { error("must not start") }
      val first = mosaic.composeAsync(tile, 1)
      recording.callback = { phase ->
        if (phase == "contribute") {
          mosaic.cancel()
          testScheduler.runCurrent()
          assertEquals(0, recording.batches.single().abandonCalls)
        }
      }
      val second = mosaic.composeAsync(tile, 2)
      testScheduler.runCurrent()
      assertFailsWith<CancellationException> { first.await() }
      assertFailsWith<CancellationException> { second.await() }
      assertTrue(recording.executions.isEmpty())
      assertEquals(1, recording.batches.single().abandonCalls)
      assertTrue(recording.batches.single().abandoned)
      recording.assertCompletedOnce()
    }

  @Test fun cancelledCreateBatchReentryAbandonsItsAccumulator() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, InlineDispatcher)
      val tile by multiTile<Int, Int> { error("must not start") }
      var nested: Deferred<Int>? = null
      var reentered = false
      recording.callback = { phase ->
        if (phase == "batch" && !reentered) {
          reentered = true
          nested = mosaic.composeAsync(tile, 2)
          mosaic.cancel()
        }
      }
      val first = mosaic.composeAsync(tile, 1)
      assertFailsWith<CancellationException> { first.await() }
      assertFailsWith<CancellationException> { nested!!.await() }
      assertTrue(recording.executions.isEmpty())
      assertEquals(1, recording.batches.size)
      assertEquals(1, recording.batches.single().abandonCalls)
      assertTrue(recording.batches.single().abandoned)
      recording.assertCompletedOnce()
    }

  /** Always dispatch synchronously, including nested launches. */
  private object InlineDispatcher : CoroutineDispatcher() {
    override fun dispatch(
      context: CoroutineContext,
      block: Runnable,
    ) = block.run()
  }
}
