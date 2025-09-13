package org.buildmosaic.core.vtwo.injection

class MosaicSceneBuilder {
  private val claims = mutableMapOf<SceneKey<*>, Any>()

  fun <T : Any> registerClaim(
    key: SceneKey<T>,
    value: T,
  ) = apply { claims[key] = value }

  fun build() = MosaicScene(claims)
}
