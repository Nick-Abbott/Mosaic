package org.buildmosaic.core.vtwo.exception

import org.buildmosaic.core.vtwo.injection.Scene
import org.buildmosaic.core.vtwo.injection.SceneKey

/**
 * Exception thrown when a requested key is not found in the [Scene]
 *
 * @param key The key that was not found
 */
class MosaicMissingKeyException(val key: SceneKey<*>) :
  IllegalArgumentException("Key ${key.name} is not available in the scene")
