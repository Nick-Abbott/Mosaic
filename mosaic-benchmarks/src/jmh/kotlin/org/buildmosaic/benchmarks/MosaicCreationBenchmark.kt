package org.buildmosaic.benchmarks

import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.injection.create
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

@State(Scope.Thread)
open class MosaicCreationBenchmark {
  @Benchmark
  open fun create(): Mosaic = emptyCanvas.create()
}
