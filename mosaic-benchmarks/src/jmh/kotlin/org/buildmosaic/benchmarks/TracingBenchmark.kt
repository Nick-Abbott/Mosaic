package org.buildmosaic.benchmarks

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.sdk.trace.samplers.Sampler
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.injection.create
import org.buildmosaic.core.singleTile
import org.buildmosaic.opentelemetry.tracing
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.OperationsPerInvocation
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import java.util.concurrent.atomic.AtomicInteger

@State(Scope.Thread)
open class TracingState {
  @JvmField @Param("ordinary", "noop", "unsampled", "recording") var mode: String = ""
  private lateinit var applicationCanvas: Canvas
  private var sdk: OpenTelemetrySdk? = null

  @Setup(Level.Trial)
  fun telemetry() {
    val telemetry = when (mode) {
      "ordinary" -> null
      "noop" -> OpenTelemetry.noop()
      "unsampled", "recording" -> {
        val builder = SdkTracerProvider.builder()
          .setSampler(if (mode == "recording") Sampler.alwaysOn() else Sampler.alwaysOff())
        if (mode == "recording") builder.addSpanProcessor(SimpleSpanProcessor.create(boundedExporter()))
        OpenTelemetrySdk.builder().setTracerProvider(builder.build()).build().also { sdk = it }
      }
      else -> error("Unknown tracing mode: $mode")
    }
    applicationCanvas = runBlocking {
      canvas { if (telemetry != null) tracing { telemetry } }
    }
  }

  fun request(): Mosaic = applicationCanvas.create()

  @TearDown(Level.Trial)
  fun close() { sdk?.close() }

  private fun boundedExporter(): SpanExporter {
    val exporter = InMemorySpanExporter.create()
    val exports = AtomicInteger()
    return object : SpanExporter by exporter {
      override fun export(spans: Collection<SpanData>): CompletableResultCode {
        val result = exporter.export(spans)
        // SimpleSpanProcessor exports one span at a time. Bound retention without invocation hooks
        // that would distort the tiny completed async cache-hit measurement. Reset cost is included.
        if (exports.incrementAndGet() % 4096 == 0) exporter.reset()
        return result
      }
    }
  }
}

@State(Scope.Thread)
open class TracingSingleState : TracingState() {
  val tile = singleTile { 7 }
  lateinit var cached: Mosaic

  @Setup(Level.Trial)
  fun cache() {
    cached = request()
    runBlocking { cached.compose(tile) }
  }
}

@State(Scope.Thread)
open class TracingGraphState : TracingState() {
  val diamond = GraphFixtures.diamond(4)
  val coalescing = CoalescingFixtures.graph(4, 0)
}

open class TracingMultiState : TracingState() {
  val keys = MultiTileFixtures.keys(16)
  lateinit var requests: Array<Mosaic>

  protected fun prepare(cached: Boolean) {
    requests = runBlocking {
      Array(BATCH_SIZE) {
        request().also { if (cached) it.compose(MultiTileFixtures.tile, keys) }
      }
    }
  }
}

@State(Scope.Thread)
open class TracingColdMultiState : TracingMultiState() {
  @Setup(Level.Invocation)
  fun requests() = prepare(false)
}

@State(Scope.Thread)
open class TracingCachedMultiState : TracingMultiState() {
  @Setup(Level.Invocation)
  fun requests() = prepare(true)
}

/** Same fixture boundaries and operation batching as the existing uninstrumented benchmarks. */
@State(Scope.Thread)
open class TracingBenchmark {
  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  open fun singleCold(state: TracingSingleState): Int = suspendBatch { state.request().compose(state.tile) }

  @Benchmark
  open fun completedAsyncCacheHit(state: TracingSingleState): Deferred<Int> = state.cached.composeAsync(state.tile)

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  open fun multiCold16(state: TracingColdMultiState): Int =
    suspendBatch { state.requests[it].compose(MultiTileFixtures.tile, state.keys).values.sum() }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  open fun multiCached16(state: TracingCachedMultiState): Int =
    suspendBatch { state.requests[it].compose(MultiTileFixtures.tile, state.keys).values.sum() }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  open fun sharedDiamond(state: TracingGraphState): Int = suspendBatch { state.request().compose(state.diamond) }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  open fun coalescingFanOut4(state: TracingGraphState): Int = suspendBatch { state.request().compose(state.coalescing) }
}
