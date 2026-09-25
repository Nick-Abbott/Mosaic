package org.buildmosaic.benchmarks

import kotlinx.coroutines.runBlocking
import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.Tile
import org.buildmosaic.core.injection.create
import org.buildmosaic.core.singleTile
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.OperationsPerInvocation
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole

@State(Scope.Thread)
open class CpuTileState {
  @JvmField @Param("32", "256", "2048") var cpuTokens: Long = 0
  lateinit var cpuTile: Tile<Int>

  @Setup
  fun prepare() {
    cpuTile = singleTile {
      Blackhole.consumeCPU(cpuTokens)
      7
    }
  }
}

@State(Scope.Thread)
open class SingleTileBenchmark {
  private val trivial = singleTile { 7 }
  private lateinit var cached: Mosaic

  @Setup
  fun prepare() {
    cached = emptyCanvas.create()
    runBlocking { cached.compose(trivial) }
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  open fun coldTrivial(): Int = suspendBatch { emptyCanvas.create().compose(trivial) }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  open fun completedCacheHit(): Int = suspendBatch { cached.compose(trivial) }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  open fun smallCpu(state: CpuTileState): Int = suspendBatch { emptyCanvas.create().compose(state.cpuTile) }
}
