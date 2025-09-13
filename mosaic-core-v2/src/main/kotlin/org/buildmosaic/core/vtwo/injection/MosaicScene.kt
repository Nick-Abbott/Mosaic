package org.buildmosaic.core.vtwo.injection

import org.buildmosaic.core.vtwo.exception.MosaicMissingKeyException

class MosaicScene internal constructor(private val claims: Map<SceneKey<*>, Any>) : Scene {
  @Throws(MosaicMissingKeyException::class)
  override fun <T : Any> claim(key: SceneKey<T>): T {
    if (key !in claims) throw MosaicMissingKeyException(key)
    @Suppress("UNCHECKED_CAST")
    return claims[key] as T
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> peek(key: SceneKey<T>): T? = claims[key] as T?

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> claimOr(
    key: SceneKey<T>,
    def: T,
  ): T = claims.getOrDefault(key, def) as T

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> claimOrCompute(
    key: SceneKey<T>,
    def: () -> T,
  ): T = claims.getOrDefault(key, def()) as T
}
