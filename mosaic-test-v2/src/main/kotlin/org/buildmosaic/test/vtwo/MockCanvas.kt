package org.buildmosaic.test.vtwo

import org.buildmosaic.core.vtwo.exception.MosaicMissingTypeException
import org.buildmosaic.core.vtwo.injection.Canvas
import kotlin.reflect.KClass

internal class MockCanvas : Canvas {
  private val registry: MutableMap<KClass<out Any>, Any> = mutableMapOf()

  fun <T : Any> register(
    type: KClass<T>,
    instance: T,
  ) {
    registry[type] = instance
  }

  override fun <T : Any> source(type: KClass<T>): T {
    if (type !in registry) {
      throw MosaicMissingTypeException(type)
    }
    @Suppress("UNCHECKED_CAST")
    return registry[type] as T
  }
}
