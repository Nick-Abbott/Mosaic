@file:OptIn(org.buildmosaic.core.instrumentation.ExperimentalMosaicInstrumentation::class)

package org.buildmosaic.opentelemetry

import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.exception.MosaicMissingMultiTileResultException
import org.buildmosaic.core.multiTile
import org.buildmosaic.core.singleTile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("FunctionMaxLength")
class PrivacyAndCancellationTest {
  @Test
  fun missingResultExportsSafeTypeWithoutKeyOrResult() =
    runTest {
      TelemetryFixture().use { otel ->
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler))
        val missing = "sensitive-missing-product-key"
        val present = "sensitive-present-product-key"
        val value = "sensitive-product-result"
        val products by multiTile<String, String> { mapOf(present to value) }
        otel.root {
          val results = mosaic.composeAsync(products, listOf(present, missing))
          testScheduler.runCurrent()
          assertEquals(value, results.getValue(present).await())
          assertFailsWith<MosaicMissingMultiTileResultException> { results.getValue(missing).await() }
        }
        val span = otel.spans.named("products")
        assertEquals(StatusCode.ERROR, span.status.statusCode)
        assertEquals(MosaicMissingMultiTileResultException::class.java.name, span.attributes.get(errorTypeKey))
        assertEquals(2L, span.attributes.get(batchSizeKey))
        assertPrivate(otel.spans, missing, present, value)
        assertTrue(otel.failures.isEmpty(), "Application errors must not reach adapter diagnostics")
      }
    }

  @Test
  fun failuresExportOnlyTypeWithoutMessagesStacksOrCauses() =
    runTest {
      TelemetryFixture().use { otel ->
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler))
        val message = "sensitive-exception-request-user-message"
        val cause = "sensitive-cause-message"
        val suppressed = "sensitive-suppressed-message"
        val failure =
          IllegalArgumentException(message, IllegalStateException(cause)).apply {
            addSuppressed(UnsupportedOperationException(suppressed))
          }
        val single by singleTile<Int> { throw failure }
        val multi by multiTile<String, Int> { throw failure }
        otel.root {
          assertFailsWith<IllegalArgumentException> { mosaic.compose(single) }
          assertFailsWith<IllegalArgumentException> { mosaic.compose(multi, "sensitive-key") }
          testScheduler.runCurrent()
        }
        for (name in listOf("single", "multi")) {
          val span = otel.spans.named(name)
          assertEquals(StatusCode.ERROR, span.status.statusCode)
          assertEquals(IllegalArgumentException::class.java.name, span.attributes.get(errorTypeKey))
          assertNull(span.attributes.get(cancelledKey))
        }
        assertPrivate(otel.spans, message, cause, suppressed, "sensitive-key", "PrivacyAndCancellationTest.kt")
        assertTrue(otel.failures.isEmpty())
      }
    }

  @Test
  fun scopeCancellationIsNotAnApplicationError() =
    runTest {
      TelemetryFixture().use { otel ->
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler))
        val single by singleTile<Int> { awaitCancellation() }
        val multi by multiTile<String, Int> { awaitCancellation() }
        otel.root {
          val a = mosaic.composeAsync(single)
          val b = mosaic.composeAsync(multi, "sensitive-cancellation-key")
          testScheduler.runCurrent()
          mosaic.cancel()
          testScheduler.runCurrent()
          assertFailsWith<CancellationException> { a.await() }
          assertFailsWith<CancellationException> { b.await() }
        }
        assertEquals(3, otel.spans.size)
        for (name in listOf("single", "multi")) {
          val span = otel.spans.named(name)
          assertEquals(StatusCode.UNSET, span.status.statusCode)
          assertNull(span.attributes.get(errorTypeKey))
          assertEquals(true, span.attributes.get(cancelledKey))
        }
        assertPrivate(otel.spans, "sensitive-cancellation-key")
        assertTrue(otel.failures.isEmpty())
      }
    }

  @Test
  fun cancellingAwaiterLeavesSharedExecutionSuccessful() =
    runTest {
      TelemetryFixture().use { otel ->
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        val shared by singleTile {
          gate.await()
          7
        }
        otel.root {
          val caller = async(start = CoroutineStart.UNDISPATCHED) { mosaic.compose(shared) }
          testScheduler.runCurrent()
          caller.cancel()
          caller.join()
          assertTrue(otel.spans.isEmpty())
          gate.complete(Unit)
          testScheduler.runCurrent()
          assertEquals(7, mosaic.compose(shared))
        }
        assertEquals(2, otel.spans.size)
        val span = otel.spans.named("shared")
        assertEquals(StatusCode.UNSET, span.status.statusCode)
        assertNull(span.attributes.get(cancelledKey))
        assertNull(span.attributes.get(errorTypeKey))
      }
    }

  @Test
  fun applicationMayDeliberatelyAnnotateCurrentSpan() =
    runTest {
      TelemetryFixture().use { otel ->
        val mosaic = otel.mosaic(StandardTestDispatcher(testScheduler))
        val tile by singleTile {
          io.opentelemetry.api.trace.Span.current().setAttribute("application.choice", "intentional")
          7
        }
        assertEquals(7, mosaic.compose(tile))
        testScheduler.runCurrent()
        assertEquals(
          "intentional",
          otel.spans.named(
            "tile",
          ).attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey("application.choice")),
        )
      }
    }
}
