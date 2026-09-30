package org.buildmosaic.core.exception

/**
 * A MultiTile returned no non-null value for [key]. The application can inspect the requested key
 * without exposing it through the exception message. Instrumentation receives only this class's name.
 */
class MosaicMissingMultiTileResultException(val key: Any) :
  NoSuchElementException("MultiTile result missing requested key")
