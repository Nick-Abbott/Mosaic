@file:OptIn(ExperimentalMosaicInstrumentation::class)

package org.buildmosaic.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.buildmosaic.core.instrumentation.ExecutionOutcome
import org.buildmosaic.core.instrumentation.ExperimentalMosaicInstrumentation
import org.buildmosaic.core.instrumentation.ProducerReference
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Suppress("LargeClass", "TooManyFunctions", "FunctionMaxLength")
class MultiTileProvenanceTest {
  @Test fun overlappingRequestsContributeAndReuseInOneBatch() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val fetched = mutableListOf<Set<Int>>()
      val multi by multiTile<Int, Int> { keys ->
        fetched.add(keys)
        keys.associateWith { it }
      }
      val first by singleTile { compose(multi, listOf(1, 2)) }
      val second by singleTile { compose(multi, listOf(2, 3)) }
      val cached by singleTile { compose(multi, listOf(1, 2, 3)) }
      val a = mosaic.composeAsync(first)
      val b = mosaic.composeAsync(second)
      testScheduler.runCurrent()
      assertEquals(mapOf(1 to 1, 2 to 2), a.await())
      assertEquals(mapOf(2 to 2, 3 to 3), b.await())
      assertEquals(listOf(setOf(1, 2, 3)), fetched)
      val batch = recording.batches.single()
      assertEquals(
        listOf(recording.execution("first").identity, recording.execution("second").identity),
        batch.callers.map { it!!.owner },
      )
      assertEquals(2, batch.contributed)
      assertTrue(batch.frozen)
      val reused = recording.execution("second").dependencies.single()
      val identity = recording.execution("multi").identity
      assertSame(identity, assertIs<ProducerReference.Published>(reused.resolution).identity)
      val c = mosaic.composeAsync(cached)
      testScheduler.runCurrent()
      assertEquals(3, c.await().size)
      assertEquals(2, batch.contributed)
      val references = recording.execution("cached").dependencies.toList()
      assertSame(references[0], references[1])
      assertFalse(references[0] === references[2])
      references.forEach { assertSame(identity, assertIs<ProducerReference.Published>(it.resolution).identity) }
      assertEquals(1, recording.executions.count { it.identity.name == "multi" })
      recording.assertCompletedOnce()
    }

  @Test fun schedulerDependentBatchesKeepFixedContributors() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val gate = CompletableDeferred<Unit>()
      val multi by multiTile<Int, Int> { keys ->
        if (1 in keys) gate.await()
        keys.associateWith { it }
      }
      val first by singleTile { compose(multi, listOf(1, 2)) }
      val second by singleTile { compose(multi, listOf(2, 3)) }
      val a = mosaic.composeAsync(first)
      testScheduler.runCurrent()
      val b = mosaic.composeAsync(second)
      testScheduler.runCurrent()
      val executions = recording.executions.filter { it.batch != null }
      assertEquals(2, executions.size)
      assertEquals(listOf(recording.execution("first").identity), executions[0].batch!!.callers.map { it!!.owner })
      assertEquals(listOf(recording.execution("second").identity), executions[1].batch!!.callers.map { it!!.owner })
      assertSame(
        executions[0].identity,
        assertIs<ProducerReference.Published>(recording.execution("second").dependencies.single().resolution).identity,
      )
      assertFalse(a.isCompleted)
      assertFalse(b.isCompleted)
      val consumer by singleTile { compose(multi, listOf(1, 3)) }
      val c = mosaic.composeAsync(consumer)
      testScheduler.runCurrent()
      val dependencies =
        recording.execution("consumer").dependencies.map {
          assertIs<ProducerReference.Published>(it.resolution).identity
        }
      assertEquals(executions.map { it.identity }.toSet(), dependencies.toSet())
      gate.complete(Unit)
      testScheduler.runCurrent()
      assertEquals(mapOf(1 to 1, 3 to 3), c.await())
      assertEquals(2, recording.batches.size)
      recording.assertCompletedOnce()
    }

  @Test fun duplicateKeysAndCachedCallsAddNoContributors() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val multi by multiTile<Int, Int> { keys -> keys.associateWith { it } }
      val a = mosaic.composeAsync(multi, listOf(1, 1, 1))
      val b = mosaic.composeAsync(multi, listOf(1, 1))
      assertSame(a.getValue(1), b.getValue(1))
      testScheduler.runCurrent()
      assertEquals(1, a.getValue(1).await())
      assertEquals(1, recording.batches.single().contributed)
      assertEquals(1, recording.captures.get())
      assertTrue(mosaic.composeAsync(multi, emptyList<Int>()).isEmpty())
      assertEquals(1, recording.batches.size)
      recording.assertCompletedOnce()
    }

  @Test fun perKeyAndChunkedHelpersKeepOneBatchExecution() =
    runTest {
      for (chunked in listOf(false, true)) {
        val recording = RecordingInstrumentation()
        val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
        val leaf by singleTile { 7 }
        val multi =
          if (chunked) {
            chunkedMultiTile<Int, Int>(2) { keys -> keys.associateWith { compose(leaf) + it } }
          } else {
            perKeyTile<Int, Int> { compose(leaf) + it }
          }
        val caller by singleTile { compose(multi, (1..5).toList()) }
        val result = mosaic.composeAsync(caller)
        testScheduler.runCurrent()
        assertEquals((1..5).associateWith { it + 7 }, result.await())
        val batchExecution = recording.executions.single { it.batch != null }
        assertSame(batchExecution.identity, recording.execution("leaf").caller!!.owner)
        assertSame(batchExecution.identity, recording.execution("leaf").caller!!.ambient)
        assertEquals(4, batchExecution.dependencies.size)
        assertEquals(3, recording.executions.size)
        recording.assertCompletedOnce()
      }
    }

  @Test fun thrownBatchFailureFinishesOnceAndFailsEveryKey() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val failure = IllegalStateException("application")
      val tile by multiTile<Int, Int> { throw failure }
      val result = mosaic.composeAsync(tile, listOf(1, 2))
      testScheduler.runCurrent()
      result.values.forEach { assertApplicationFailure(failure, assertFailsWith<IllegalStateException> { it.await() }) }
      val completion = recording.execution("tile").completions.single()
      assertEquals(ExecutionOutcome.FAILURE, completion.outcome)
      assertSame(failure, completion.failure)
      assertSame(result.getValue(1), mosaic.composeAsync(tile, 1))
      recording.assertCompletedOnce()
    }

  @Test fun missingBatchResultReportsFailureAndPreservesOtherValues() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val tile by multiTile<Int, Int> { mapOf(1 to 7) }
      val result = mosaic.composeAsync(tile, listOf(1, 2))
      testScheduler.runCurrent()
      assertEquals(7, result.getValue(1).await())
      val failure = assertFailsWith<NoSuchElementException> { result.getValue(2).await() }
      val completion = recording.execution("tile").completions.single()
      assertEquals(ExecutionOutcome.FAILURE, completion.outcome)
      assertApplicationFailure(completion.failure!!, failure)
      recording.assertCompletedOnce()
    }

  @Test fun scopeCancellationFinishesStartedBatchAndAbandonsPendingBatch() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val tile by multiTile<Int, Int> { awaitCancellation() }
      val result = mosaic.composeAsync(tile, listOf(1, 2))
      testScheduler.runCurrent()
      val pending = mosaic.composeAsync(tile, listOf(3, 4))
      mosaic.cancel()
      testScheduler.runCurrent()
      (result.values + pending.values).forEach { assertFailsWith<CancellationException> { it.await() } }
      assertEquals(1, recording.executions.size)
      assertEquals(ExecutionOutcome.CANCELLED, recording.execution("tile").completions.single().outcome)
      assertTrue(recording.batches.last().abandoned)
      recording.assertCompletedOnce()
    }

  @Test fun pendingBatchAbandonsPublicationAndContributorState() =
    runTest {
      val recording = RecordingInstrumentation()
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val tile by multiTile<Int, Int> { error("must not start") }
      val caller by singleTile {
        composeAsync(tile, listOf(1, 2))
        composeAsync(tile, 1)
        mosaic.cancel()
        7
      }
      mosaic.composeAsync(caller)
      testScheduler.runCurrent()
      val execution = recording.execution("caller")
      assertSame(ProducerReference.Abandoned, execution.dependencies.single().resolution)
      assertTrue(execution.finalized.get())
      assertEquals(1, recording.executions.size)
      assertTrue(recording.batches.single().abandoned)
      assertTrue(recording.batches.single().callers.isEmpty())
      recording.assertCompletedOnce()
    }

  @Test fun manyContributorsAndEarlierBatchesCanBeBounded() =
    runTest {
      val recording = RecordingInstrumentation(limit = 4)
      val mosaic = MosaicImpl.instrumented(emptyCanvas, recording, StandardTestDispatcher(testScheduler))
      val tile by multiTile<Int, Int> { keys -> keys.associateWith { it } }
      repeat(256) { mosaic.composeAsync(tile, it) }
      testScheduler.runCurrent()
      assertEquals(256, recording.batches.single().contributed)
      assertEquals(4, recording.batches.single().callers.size)
      repeat(64) {
        mosaic.composeAsync(tile, it + 256)
        testScheduler.runCurrent()
      }
      val consumer by singleTile { compose(tile, (0 until 320).toList()) }
      val result = mosaic.composeAsync(consumer)
      testScheduler.runCurrent()
      assertEquals(320, result.await().size)
      val execution = recording.execution("consumer")
      assertEquals(320, execution.reported.get())
      assertEquals(4, execution.dependencies.size)
      assertEquals(65, recording.batches.size)
      recording.assertCompletedOnce()
    }

  @Test fun concurrentReservationsNeverDuplicateWorkOrLosePublication() =
    runBlocking {
      repeat(20) {
        val recording = RecordingInstrumentation(limit = 4)
        val mosaic = MosaicImpl.instrumented(emptyCanvas, recording)
        val fetched = ConcurrentLinkedQueue<Set<Int>>()
        val tile by multiTile<Int, Int> { keys ->
          fetched.add(keys)
          keys.associateWith { it }
        }
        withTimeout(10_000) {
          val requests = List(32) { index -> singleTile { compose(tile, (index until index + 16).toList()) } }
          val results = requests.map { async { mosaic.compose(it) } }.awaitAll()
          assertTrue(results.all { map -> map.all { it.key == it.value } })
          mosaic.coroutineContext[kotlinx.coroutines.Job]!!.children.toList().forEach { it.join() }
        }
        assertEquals((0 until 47).toSet(), fetched.flatMap { it }.toSet())
        assertEquals(47, fetched.sumOf { it.size })
        recording.executions.flatMap { it.dependencies }.forEach {
          assertIs<ProducerReference.Published>(
            it.resolution,
          )
        }
        recording.assertCompletedOnce()
        assertTrue(recording.failures.isEmpty())
        mosaic.cancel()
      }
    }
}
