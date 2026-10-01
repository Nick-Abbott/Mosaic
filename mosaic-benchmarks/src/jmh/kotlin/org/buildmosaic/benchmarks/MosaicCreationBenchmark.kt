package org.buildmosaic.benchmarks

import kotlinx.coroutines.runBlocking
import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.injection.create
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State

@State(Scope.Thread)
open class MosaicCreationBenchmark {
  private lateinit var applicationCanvas: Canvas

  @Setup
  fun canvas() {
    applicationCanvas = runBlocking { canvas {} }
  }

  @Benchmark
  open fun create(): Mosaic = emptyCanvas.create()

  @Benchmark
  open fun createFromCanvas(): Mosaic = applicationCanvas.create()
}
