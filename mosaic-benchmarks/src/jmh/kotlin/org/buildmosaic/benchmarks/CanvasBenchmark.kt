package org.buildmosaic.benchmarks

import kotlinx.coroutines.runBlocking
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.injection.CanvasKey
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.OperationsPerInvocation
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole

@State(Scope.Thread)
open class CanvasLookupState {
  @JvmField @Param("0", "1", "4", "16") var parentDepth: Int = 0
  lateinit var lookupCanvas: Canvas

  @Setup
  fun prepare() {
    lookupCanvas = runBlocking { CanvasFixtures.lookupCanvas(parentDepth) }
  }
}

@State(Scope.Thread)
open class CanvasLayerState {
  @JvmField @Param("0", "8", "32", "128") var bindingCount: Int = 0
  lateinit var bindingKeys: List<CanvasKey<Int>>

  @Setup
  fun prepare() {
    bindingKeys = CanvasFixtures.bindingKeys(bindingCount)
  }
}

@State(Scope.Thread)
open class CanvasBenchmark {
  @Benchmark
  open fun lookup(state: CanvasLookupState): Int = state.lookupCanvas.source(CanvasFixtures.lookupKey)

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  open fun createLayer(state: CanvasLayerState, blackhole: Blackhole) =
    suspendBatchObjects(blackhole) { CanvasFixtures.child(emptyCanvas, state.bindingKeys) }
}
