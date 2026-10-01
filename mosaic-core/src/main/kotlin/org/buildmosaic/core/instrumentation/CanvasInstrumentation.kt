package org.buildmosaic.core.instrumentation

import org.buildmosaic.core.injection.CanvasBuilder
import org.buildmosaic.core.injection.create

/**
 * Integration-author API installing one runtime provider for [create] on this Canvas and its
 * descendant layers. The provider configures durable runtime settings outside application DI.
 * [provider] runs once during Canvas setup, after rejecting any local or inherited installation.
 * This integration hook does not change ordinary Mosaic constructors or compose/cache paths.
 */
fun CanvasBuilder.installInstrumentation(provider: () -> MosaicInstrumentation) {
  configureRuntime().installInstrumentation(provider)
}
