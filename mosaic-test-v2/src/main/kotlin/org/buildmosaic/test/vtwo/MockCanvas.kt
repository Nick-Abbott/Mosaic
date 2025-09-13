package org.buildmosaic.test.vtwo

import org.buildmosaic.core.vtwo.injection.Canvas
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

internal class MockCanvas : Canvas {
  private val registry = ConcurrentHashMap<KClass<out Any>, Any>()

  fun <T : Any> register(
    type: KClass<T>,
    instance: T,
  ) {
    registry[type] = instance
  }

  override fun <T : Any> source(type: KClass<T>): T {
    require(registry.containsKey(type)) {
      "There is no injection for $type"
    }
    @Suppress("UNCHECKED_CAST")
    return registry[type] as T
  }
}
