package org.buildmosaic.benchmarks

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.OperationsPerInvocation
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

@State(Scope.Thread)
open class ControlBenchmark {
  private fun direct(): Int = 7

  private suspend fun suspended(): Int = 7

  @Benchmark
  open fun directKotlin(): Int = direct()

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  open fun suspendBridge(): Int = suspendBatch { suspended() }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  open fun coroutineLaunchAwait(): Int =
    suspendBatch { coroutineScope { async { 7 }.await() } }
}
