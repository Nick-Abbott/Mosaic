package org.buildmosaic.core.exception

import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.injection.CanvasKey

/**
 * Exception thrown when a requested key is not found in the [Canvas]
 *
 * @param key The key that was not found
 */
class MosaicMissingKeyException(val key: CanvasKey<*>) :
  IllegalArgumentException("$key is not available in the canvas")
