package org.buildmosaic.benchmarks

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.buildmosaic.core.injection.create
import org.buildmosaic.core.singleTile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExecutionDrainTest {
  @Test fun drainWaitsForAttachedChildrenAfterResult() = runBlocking {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    var finished = false
    val root = singleTile {
      CoroutineScope(currentCoroutineContext()).launch {
        entered.complete(Unit)
        release.await()
        finished = true
      }
      42
    }
    val mosaic = emptyCanvas.create()
    val drained = async { composeAndDrain(mosaic, root) }
    entered.await()
    assertEquals(42, mosaic.compose(root))
    assertFalse(drained.isCompleted)
    release.complete(Unit)
    assertEquals(42, drained.await())
    assertTrue(finished)
    val job = checkNotNull((mosaic as CoroutineScope).coroutineContext[Job])
    assertTrue(job.isCompleted)
    assertTrue(job.children.none())
  }

  @Test fun graphResultsSurviveFullDrain() = runBlocking {
    assertEquals(GraphFixtures.diamondResult(4), composeAndDrain(emptyCanvas.create(), GraphFixtures.diamond(4)))
    assertEquals(CoalescingFixtures.expected(4), composeAndDrain(emptyCanvas.create(), CoalescingFixtures.graph(4, 0)))
  }
}
