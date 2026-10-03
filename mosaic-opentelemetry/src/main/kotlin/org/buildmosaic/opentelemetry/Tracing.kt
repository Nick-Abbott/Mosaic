package org.buildmosaic.opentelemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.TracerProvider
import org.buildmosaic.core.injection.CanvasBuilder
import org.buildmosaic.core.observation.installExecutionObserver

/**
 * Traces actual Tile executions using an already registered global provider, including Java agents.
 * Fails at Canvas setup if no tracing provider is registered, without initializing global state.
 */
fun CanvasBuilder.tracing() {
  tracing {
    val telemetry = GlobalOpenTelemetry.getOrNoop()
    check(telemetry.tracerProvider !== TracerProvider.noop()) {
      "tracing() requires a registered OpenTelemetry tracing provider. " +
        "Start the OpenTelemetry Java agent or use tracing { openTelemetry }."
    }
    telemetry
  }
}

/**
 * Traces actual Tile executions using the application's provider. The factory runs once at Canvas
 * setup, after duplicate validation; descendants inherit this installation. Separate request Mosaics
 * share configuration, never cached work. Explicit no-op providers are accepted.
 *
 * The application owns its SDK, sampling, processors, export, and shutdown. Mosaic records structural
 * execution data only; it never records keys, results, Canvas values, or raw exceptions.
 */
fun CanvasBuilder.tracing(openTelemetry: () -> OpenTelemetry) {
  installExecutionObserver { OpenTelemetryObserver(openTelemetry().getTracer("org.buildmosaic.mosaic")) }
}
