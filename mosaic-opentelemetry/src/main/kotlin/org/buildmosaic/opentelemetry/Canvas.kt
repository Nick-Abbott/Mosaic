@file:OptIn(ExperimentalMosaicInstrumentation::class)

package org.buildmosaic.opentelemetry

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.buildmosaic.core.Mosaic
import org.buildmosaic.core.MosaicImpl
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.instrumentation.ExperimentalMosaicInstrumentation

/** Creates a request-scoped Mosaic using an application-owned OpenTelemetry instance. */
fun Canvas.create(
  instrumentation: OpenTelemetryMosaicInstrumentation,
  dispatcher: CoroutineDispatcher = Dispatchers.Default,
): Mosaic = MosaicImpl.instrumented(this, instrumentation, dispatcher)
