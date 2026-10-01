package org.buildmosaic.opentelemetry

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.Clock
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.samplers.Sampler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.MosaicImpl
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.injection.CanvasKey
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.injection.create
import org.buildmosaic.core.instrumentation.MosaicInstrumentation
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal val emptyCanvas =
  object : Canvas {
    override fun <T : Any> sourceOr(key: CanvasKey<T>): T? = null
  }

internal class TelemetryFixture(
  sampler: Sampler = Sampler.alwaysOn(),
  maxDependencyLinks: Int = 64,
  maxContributorLinks: Int = 64,
  maxPendingDependencies: Int = 64,
  clock: Clock = Clock.getDefault(),
) : AutoCloseable {
  val exporter = InMemorySpanExporter.create()
  val provider =
    SdkTracerProvider.builder().setSampler(sampler).setClock(clock)
      .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()
  val telemetry = OpenTelemetrySdk.builder().setTracerProvider(provider).build()
  val failures = ConcurrentLinkedQueue<Throwable>()
  val instrumentation =
    OpenTelemetryMosaicInstrumentation(
      telemetry,
      maxDependencyLinks,
      maxContributorLinks,
      maxPendingDependencies,
      failures::add,
    )
  val tracer = telemetry.getTracer("test.application")
  private val mosaics = mutableListOf<MosaicImpl>()
  val spans: List<SpanData> get() = exporter.finishedSpanItems

  suspend fun mosaic(
    dispatcher: CoroutineDispatcher,
    adapter: MosaicInstrumentation = instrumentation,
    canvas: Canvas = emptyCanvas,
  ): MosaicImpl {
    val mosaic =
      if (adapter === instrumentation && dispatcher === Dispatchers.Default) {
        canvas(canvas) { tracing { telemetry } }.create()
      } else {
        MosaicImpl.instrumented(canvas, adapter, dispatcher)
      }
    return track(mosaic)
  }

  fun track(mosaic: Mosaic): MosaicImpl = (mosaic as MosaicImpl).also { mosaics.add(it) }

  suspend fun <T> root(block: suspend (Span) -> T): T {
    val root = tracer.spanBuilder("Root").setNoParent().setSpanKind(SpanKind.SERVER).startSpan()
    try {
      return withContext(root.asContextElement()) { block(root) }
    } finally {
      root.end()
    }
  }

  fun downstream(name: String) {
    tracer.spanBuilder(name).setSpanKind(SpanKind.CLIENT).startSpan().end()
  }

  override fun close() {
    mosaics.forEach { it.cancel() }
    telemetry.close()
  }
}

internal class ManualDispatcher : CoroutineDispatcher() {
  private val tasks = ConcurrentLinkedQueue<Runnable>()

  override fun dispatch(
    context: CoroutineContext,
    block: Runnable,
  ) {
    tasks.add(block)
  }

  fun runNext() = checkNotNull(tasks.poll()) { "No scheduled work" }.run()

  fun drain() {
    while (true) (tasks.poll() ?: return).run()
  }
}

internal fun List<SpanData>.named(name: String): SpanData = single { it.name == name }

internal fun assertLinks(
  span: SpanData,
  vararg targets: Pair<SpanData, String>,
) {
  assertEquals(
    targets.map { (target, type) -> Triple(target.traceId, target.spanId, type) }.toSet(),
    span.links.map { Triple(it.spanContext.traceId, it.spanContext.spanId, it.attributes.get(linkTypeKey)) }.toSet(),
  )
  assertEquals(targets.size, span.links.size)
  span.links.forEach { assertEquals(1, it.attributes.size()) }
}

internal val linkTypeKey = io.opentelemetry.api.common.AttributeKey.stringKey("mosaic.link.type")

internal fun assertPrivate(
  spans: List<SpanData>,
  vararg secrets: String,
) {
  val data = spans.joinToString("\n")
  secrets.forEach { secret -> assertTrue(secret !in data, "Telemetry contains a sensitive value") }
  spans.forEach {
    assertTrue(it.events.isEmpty(), "Adapter must not record exceptions or application events")
    assertTrue(it.status.description.isEmpty())
  }
}

internal suspend fun MosaicImpl.awaitExecutions() {
  coroutineContext[Job]!!.children.toList().forEach { it.join() }
}
