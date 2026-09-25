package org.buildmosaic.benchmarks

import kotlinx.coroutines.runBlocking
import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.injection.create
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State

@State(Scope.Thread)
open class MosaicCreationBenchmark {
  @JvmField @Param("empty", "representative") var canvasKind = ""

  private lateinit var preparedCanvas: Canvas

  @Setup
  fun prepare() {
    preparedCanvas = runBlocking {
      when (canvasKind) {
        "empty" -> canvas {}
        "representative" -> canvas {
          repeat(16) { index -> single<Int>("dependency-$index") { index } }
        }
        else -> error("Unknown Canvas kind: $canvasKind")
      }
    }
  }

  @Benchmark
  open fun create(): Mosaic = preparedCanvas.create()
}
