package org.buildmosaic.core.instrumentation

import org.buildmosaic.core.injection.CanvasBuilder
import org.buildmosaic.core.injection.create

/**
 * Installs one provider for [create] on this Canvas and its descendant layers, outside application DI.
 * [provider] runs once during Canvas setup, after rejecting any local or inherited installation.
 * This integration hook does not change ordinary Mosaic constructors or compose/cache paths.
 */
@ExperimentalMosaicInstrumentation
fun CanvasBuilder.installInstrumentation(provider: () -> MosaicInstrumentation) {
  check(!installingInstrumentation && instrumentation == null) {
    "Mosaic instrumentation is already configured on this Canvas or an ancestor. " +
      "Install it once on the application Canvas."
  }
  installingInstrumentation = true
  try {
    instrumentation = provider()
  } finally {
    installingInstrumentation = false
  }
}
