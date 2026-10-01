package org.buildmosaic.test

import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.instrumentation.ExecutionCompletion
import org.buildmosaic.core.instrumentation.MosaicInstrumentation
import org.buildmosaic.core.instrumentation.ProducerReference
import org.buildmosaic.core.instrumentation.installInstrumentation
import org.buildmosaic.core.multiTile
import org.buildmosaic.core.singleTile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class SharedRuntimeTest {
  @Test fun nestedSubstitutionsUseTheSharedRuntime() =
    runTest {
      for (enabled in listOf(false, true)) {
        val provider = Observations()
        val dependencies = canvas { if (enabled) installInstrumentation { provider } }
        val leaf = singleTile<Int> { error("SingleTile must be substituted") }
        val many = multiTile<String, Int> { error("MultiTile must be substituted") }
        val replacementLeaf by singleTile { 5 }
        val replacementMany by multiTile<String, Int> { keys -> keys.associateWith { compose(leaf) + 1 } }
        val response by singleTile { compose(many, listOf("a", "b")) }
        val mosaic =
          TestMosaic(
            dependencies,
            mapOf(leaf to replacementLeaf),
            mapOf(many to replacementMany),
            StandardTestDispatcher(testScheduler),
          )
        val first = mosaic.composeAsync(response)
        assertFalse(first.isCompleted)
        assertSame(first, mosaic.composeAsync(response))
        testScheduler.runCurrent()
        assertEquals(mapOf("a" to 6, "b" to 6), first.await())
        assertEquals(6, mosaic.compose(many, "a"))
        assertEquals(
          if (enabled) listOf("response", "replacementMany", "replacementLeaf") else emptyList(),
          provider.started,
        )
        assertEquals(if (enabled) 3 else 0, provider.completed)
        mosaic.cancel()
      }
    }

  private class Observations : MosaicInstrumentation {
    val started = mutableListOf<String?>()
    var completed = 0

    override fun captureCaller(execution: MosaicInstrumentation.ExecutionIdentity?) = null

    override fun startSingle(
      name: String?,
      caller: MosaicInstrumentation.CallerContext?,
    ): MosaicInstrumentation.Execution = execution(name)

    override fun createBatch(): MosaicInstrumentation.Batch =
      object : MosaicInstrumentation.Batch {
        override fun contribute(caller: MosaicInstrumentation.CallerContext?) = Unit

        override fun start(
          name: String?,
          batchSize: Int,
        ): MosaicInstrumentation.Execution = execution(name)

        override fun abandon() = error("Unexpected abandonment")
      }

    override fun onCallbackFailure(failure: Throwable) = throw AssertionError("Provider callback failed", failure)

    private fun execution(name: String?): MosaicInstrumentation.Execution {
      started.add(name)
      return object : MosaicInstrumentation.Execution {
        override val identity = object : MosaicInstrumentation.ExecutionIdentity {}

        override fun dependency(producer: ProducerReference) = Unit

        override fun complete(completion: ExecutionCompletion) {
          completed++
        }
      }
    }
  }
}
