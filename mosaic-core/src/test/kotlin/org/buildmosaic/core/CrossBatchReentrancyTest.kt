package org.buildmosaic.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.buildmosaic.core.instrumentation.MosaicInstrumentation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrossBatchReentrancyTest {
  @Test fun crossBatchCallbacksCanReenterConcurrently() {
    val recording = RecordingInstrumentation()
    val entered = CountDownLatch(2)
    val contributed = CountDownLatch(2)
    val returned = CountDownLatch(2)
    val results = ConcurrentHashMap<String, Deferred<Int>>()
    val failures = ConcurrentHashMap<String, Throwable>()
    lateinit var mosaic: MosaicImpl
    val first by multiTile<Int, Int> { keys -> keys.associateWith { it } }
    val second by multiTile<Int, Int> { keys -> keys.associateWith { it * 10 } }
    val provider =
      object : MosaicInstrumentation by recording {
        override fun createBatch(): MosaicInstrumentation.Batch {
          val batch = recording.createBatch()
          return object : MosaicInstrumentation.Batch by batch {
            private var initial = true

            override fun contribute(caller: MosaicInstrumentation.CallerContext?) {
              if (initial) {
                initial = false
                entered.countDown()
                check(entered.await(3, TimeUnit.SECONDS)) { "Both callbacks must enter" }
                val other = if (Thread.currentThread().name == "first") second else first
                results["nested-${Thread.currentThread().name}"] = mosaic.composeAsync(other, 2)
                contributed.countDown()
                check(contributed.await(3, TimeUnit.SECONDS)) { "Both contributions must be accepted" }
              }
              batch.contribute(caller)
            }
          }
        }
      }
    mosaic = instrumentedMosaic(emptyCanvas, provider, InlineDispatcher)
    try {
      for ((name, tile) in listOf("first" to first, "second" to second)) {
        // A lock inversion must fail this test without keeping the test JVM alive.
        thread(name = name, isDaemon = true) {
          try {
            results[name] = mosaic.composeAsync(tile, 1)
          } catch (failure: Throwable) {
            failures[name] = failure
          } finally {
            returned.countDown()
          }
        }
      }
      assertTrue(returned.await(5, TimeUnit.SECONDS), "Provider reentry deadlocked across batches")
      assertTrue(failures.isEmpty(), failures.toString())
      runBlocking {
        withTimeout(3_000) {
          assertEquals(1, results.getValue("first").await())
          assertEquals(10, results.getValue("second").await())
          assertEquals(20, results.getValue("nested-first").await())
          assertEquals(2, results.getValue("nested-second").await())
        }
      }
      assertEquals(2, recording.batches.size)
      recording.batches.forEach { assertEquals(2, it.contributed) }
      recording.assertCompletedOnce()
    } finally {
      mosaic.cancel()
    }
  }

  private object InlineDispatcher : CoroutineDispatcher() {
    override fun dispatch(
      context: CoroutineContext,
      block: Runnable,
    ) = block.run()
  }
}
