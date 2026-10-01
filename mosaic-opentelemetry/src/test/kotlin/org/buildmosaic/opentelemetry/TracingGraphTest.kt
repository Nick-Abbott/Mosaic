@file:Suppress("VariableNaming", "ktlint:standard:property-naming")

package org.buildmosaic.opentelemetry

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.multiTile
import org.buildmosaic.core.singleTile
import org.buildmosaic.core.source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TracingGraphTest {
  @Test
  @Suppress("LongMethod")
  fun graphHasExactParentsDependenciesAndContributors() =
    runTest {
      TelemetryFixture().use { otel ->
        val requestData = "sensitive-canvas-request-user"
        val resultData = "sensitive-tile-result"
        val keys = listOf("sensitive-key-d", "sensitive-key-e", "sensitive-key-f")
        val requestCanvas = canvas { single<String> { requestData } }
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler), canvas = requestCanvas)
        val Shared by singleTile {
          assertEquals(requestData, source<String>())
          otel.downstream("downstream.shared")
          resultData
        }
        val Products by multiTile<String, String> { batch ->
          otel.downstream("downstream.products")
          batch.associateWith { resultData }
        }
        val B by singleTile { compose(Shared) }
        val C by singleTile { compose(Shared) }
        val D by singleTile { compose(Products, keys[0]) }
        val E by singleTile { compose(Products, keys[1]) }
        val F by singleTile { compose(Products, keys[2]) }
        val CachedSingle by singleTile { compose(Shared) }
        val CachedMulti by singleTile { compose(Products, keys) }
        otel.root {
          val callers = listOf(B, C, D, E, F).map(mosaic::composeAsync)
          testScheduler.runCurrent()
          callers.forEach { assertEquals(resultData, it.await()) }
          val cachedSingle = mosaic.composeAsync(CachedSingle)
          val cachedMulti = mosaic.composeAsync(CachedMulti)
          testScheduler.runCurrent()
          assertEquals(resultData, cachedSingle.await())
          assertEquals(keys.associateWith { resultData }, cachedMulti.await())
        }
        val spans = otel.spans
        val names =
          setOf(
            "Root", "B", "C", "D", "E", "F", "Shared", "Products", "CachedSingle", "CachedMulti",
            "downstream.shared", "downstream.products",
          )
        assertEquals(names, spans.map { it.name }.toSet())
        assertEquals(names.size, spans.size)
        assertEquals(1, spans.map { it.traceId }.toSet().size)
        val parents =
          mapOf(
            "B" to "Root", "C" to "Root", "D" to "Root", "E" to "Root", "F" to "Root",
            "CachedSingle" to "Root", "CachedMulti" to "Root", "Shared" to "B", "Products" to "D",
            "downstream.shared" to "Shared", "downstream.products" to "Products",
          )
        parents.forEach { (child, parent) -> assertEquals(spans.named(parent).spanId, spans.named(child).parentSpanId) }
        assertEquals("0000000000000000", spans.named("Root").parentSpanId)
        spans.forEach { span ->
          val kind =
            when (span.name) {
              "Root" -> SpanKind.SERVER
              "downstream.shared", "downstream.products" -> SpanKind.CLIENT
              else -> SpanKind.INTERNAL
            }
          assertEquals(kind, span.kind)
          assertEquals(StatusCode.UNSET, span.status.statusCode)
          assertNull(span.attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey("mosaic.tile.name")))
          when (span.name) {
            "C", "CachedSingle" -> assertLinks(span, spans.named("Shared") to "dependency")
            "CachedMulti" -> assertLinks(span, spans.named("Products") to "dependency")
            "Products" -> assertLinks(span, spans.named("E") to "contributor", spans.named("F") to "contributor")
            else -> assertLinks(span)
          }
          if (span.kind == SpanKind.INTERNAL) {
            assertEquals(if (span.name == "Products") "multi" else "single", span.attributes.get(tileKind))
            assertEquals(if (span.name == "Products") 2 else 1, span.attributes.size())
          } else {
            assertEquals(0, span.attributes.size())
          }
        }
        assertEquals(3L, spans.named("Products").attributes.get(batchSizeKey))
        assertPrivate(spans, requestData, resultData, *keys.toTypedArray())
        assertTrue(otel.failures.isEmpty())
      }
    }

  @Test
  fun nestedTilesKeepContextAcrossSuspensionAndDispatch() =
    runTest {
      TelemetryFixture().use { otel ->
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler))
        val seen = mutableListOf<String>()
        val inner by singleTile {
          otel.downstream("downstream.inner")
          7
        }
        val outer by singleTile {
          seen.add(Span.current().spanContext.spanId)
          yield()
          delay(1)
          seen.add(Span.current().spanContext.spanId)
          withContext(Dispatchers.Default) {
            seen.add(Span.current().spanContext.spanId)
            otel.downstream("downstream.switched")
            compose(inner)
          }
        }
        otel.root { root ->
          assertEquals(7, mosaic.compose(outer))
          assertEquals(root.spanContext, Span.current().spanContext)
        }
        testScheduler.runCurrent()
        val spans = otel.spans
        assertEquals(5, spans.size)
        assertEquals(List(3) { spans.named("outer").spanId }, seen)
        assertEquals(spans.named("Root").spanId, spans.named("outer").parentSpanId)
        assertEquals(spans.named("outer").spanId, spans.named("inner").parentSpanId)
        assertEquals(spans.named("outer").spanId, spans.named("downstream.switched").parentSpanId)
        assertEquals(spans.named("inner").spanId, spans.named("downstream.inner").parentSpanId)
        assertEquals(1, spans.map { it.traceId }.toSet().size)
        spans.forEach { assertLinks(it) }
      }
    }

  @Test
  fun unnamedAndDelegatedTilesHaveStableNames() =
    runTest {
      TelemetryFixture().use { otel ->
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler))
        val namedSingle by singleTile { 1 }
        val namedMulti by multiTile<Int, Int> { it.associateWith { key -> key } }
        val alias by namedSingle
        otel.root {
          assertEquals(1, mosaic.compose(alias))
          mosaic.compose(namedMulti, 1)
          mosaic.compose(singleTile { 2 })
          mosaic.compose(multiTile<Int, Int> { it.associateWith { key -> key } }, 2)
        }
        testScheduler.runCurrent()
        assertEquals(
          setOf("Root", "namedSingle", "namedMulti", "Mosaic single", "Mosaic multi"),
          otel.spans.map { it.name }.toSet(),
        )
        assertEquals(5, otel.spans.size)
        assertEquals(1L, otel.spans.named("Mosaic multi").attributes.get(batchSizeKey))
      }
    }
}
