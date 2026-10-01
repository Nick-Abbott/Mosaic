@file:OptIn(org.buildmosaic.core.instrumentation.ExperimentalMosaicInstrumentation::class)

package org.buildmosaic.opentelemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.TracerProvider
import org.buildmosaic.core.injection.CanvasBuilder
import org.buildmosaic.core.instrumentation.installInstrumentation

/**
 * Traces Mosaic work using the registered global OpenTelemetry instance, including Java-agent setups.
 * Fails during Canvas setup if no tracing provider is registered. Configuration applies to child layers.
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
 * Traces Mosaic work using the application's OpenTelemetry instance, without consulting global state.
 * [openTelemetry] runs once during Canvas setup. Configuration applies to child layers; the application
 * owns the provider, sampling, exporting, and shutdown.
 */
fun CanvasBuilder.tracing(openTelemetry: () -> OpenTelemetry) {
  installInstrumentation { OpenTelemetryMosaicInstrumentation(openTelemetry()) }
}
