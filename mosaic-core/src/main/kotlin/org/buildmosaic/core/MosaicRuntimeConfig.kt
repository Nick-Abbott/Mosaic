package org.buildmosaic.core

import org.buildmosaic.core.observation.ExecutionObserver

/** Durable runtime choices, separate from Canvas dependencies and request execution state. */
internal class MosaicRuntimeConfig(val executionObserver: ExecutionObserver? = null) {
  companion object {
    val EMPTY = MosaicRuntimeConfig()
  }
}
